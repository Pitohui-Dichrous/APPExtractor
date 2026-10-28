package app.appextractor.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Streams original signed APK bytes. No temporary files or reconstructed signatures. */
public final class ExportEngine {
    private static final int BUFFER_SIZE = 64 * 1024;

    private ExportEngine() {}

    public enum Format { APK, APKS, ZIP }

    @FunctionalInterface
    public interface Progress {
        void onProgress(long done, long total);
    }

    /** Captures source metadata when the user starts an export. */
    public static final class Source {
        private final String entryName;
        private final File file;
        private final long length;
        private final long modified;

        public Source(String entryName, File file) {
            this.entryName = Objects.requireNonNull(entryName, "entryName");
            this.file = Objects.requireNonNull(file, "file").getAbsoluteFile();
            if (!this.file.isFile()) throw new IllegalArgumentException("安装包文件不存在：" + file);
            this.length = this.file.length();
            this.modified = this.file.lastModified();
        }

        public String entryName() { return entryName; }
        public File file() { return file; }
        public long length() { return length; }

        private void validate() throws IOException {
            if (!file.isFile() || file.length() != length || file.lastModified() != modified) {
                throw new IOException("安装包已变更，请刷新应用列表后重试：" + file.getName());
            }
        }
    }

    public static final class App {
        private final String label;
        private final String packageName;
        private final String versionName;
        private final List<Source> sources;

        public App(String label, String packageName, String versionName, List<Source> sources) {
            this.label = Objects.requireNonNull(label, "label");
            this.packageName = Objects.requireNonNull(packageName, "packageName");
            this.versionName = Objects.requireNonNull(versionName, "versionName");
            this.sources = immutable(sources);
            if (this.sources.isEmpty()) throw new IllegalArgumentException("应用没有可读取的 APK");
        }

        public String label() { return label; }
        public String packageName() { return packageName; }
        public String versionName() { return versionName; }
        public List<Source> sources() { return sources; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof App)) return false;
            App app = (App) other;
            return label.equals(app.label) && packageName.equals(app.packageName)
                    && versionName.equals(app.versionName) && sources.equals(app.sources);
        }

        @Override public int hashCode() {
            int result = label.hashCode();
            result = 31 * result + packageName.hashCode();
            result = 31 * result + versionName.hashCode();
            return 31 * result + sources.hashCode();
        }

        @Override public String toString() {
            return "App[label=" + label + ", packageName=" + packageName
                    + ", versionName=" + versionName + ", sources=" + sources + "]";
        }
    }

    private static final class Entry {
        private final String name;
        private final Source source;

        private Entry(String name, Source source) {
            this.name = name;
            this.source = source;
        }

        public String name() { return name; }
        public Source source() { return source; }
    }

    public static final class Plan {
        private final List<App> apps;
        private final List<Entry> entries;
        private final Format format;
        private final String filename;
        private final long totalBytes;

        private Plan(List<App> apps, List<Entry> entries, Format format, String filename, long totalBytes) {
            this.apps = immutable(apps);
            this.entries = immutable(entries);
            this.format = format;
            this.filename = filename;
            this.totalBytes = totalBytes;
        }

        public List<App> apps() { return apps; }
        public Format format() { return format; }
        public String filename() { return filename; }
        public long totalBytes() { return totalBytes; }
        public String mimeType() {
            return switch (format) {
                case APK -> "application/vnd.android.package-archive";
                case APKS -> "application/octet-stream";
                case ZIP -> "application/zip";
            };
        }
    }

    /** One app produces .apk or .apks; more than one produces an archive of complete packages. */
    public static Plan plan(List<App> requestedApps) {
        List<App> apps = immutable(requestedApps);
        if (apps.isEmpty()) throw new IllegalArgumentException("请选择应用");
        Format format = apps.size() > 1 ? Format.ZIP
                : apps.get(0).sources().size() > 1 ? Format.APKS : Format.APK;
        List<Entry> entries = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        long total = 0;
        for (App app : apps) {
            String stem = stemName(app);
            if (app.sources().size() == 1) {
                Source source = app.sources().get(0);
                entries.add(new Entry(unique(stem + ".apk", paths), source));
                total = addLength(total, source.length());
            } else {
                String prefix = format == Format.ZIP ? unique(stem, paths) + "/" : "";
                Set<String> splitNames = new HashSet<>();
                for (int i = 0; i < app.sources().size(); i++) {
                    Source source = app.sources().get(i);
                    String splitName = i == 0 ? "base.apk" : apkName(source.entryName());
                    entries.add(new Entry(prefix + unique(splitName, splitNames), source));
                    total = addLength(total, source.length());
                }
            }
        }
        String filename;
        if (format == Format.ZIP) {
            filename = "APPExtractor_" + apps.size() + "_apps.zip";
        } else {
            App app = apps.get(0);
            filename = stemName(app) + (format == Format.APK ? ".apk" : ".apks");
        }
        return new Plan(apps, entries, format, filename, total);
    }

    /** Output remains owned by the caller, including on failure or cancellation. */
    public static void write(Plan plan, OutputStream output, BooleanSupplier cancelled, Progress progress)
            throws IOException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(cancelled, "cancelled");
        Objects.requireNonNull(progress, "progress");
        checkCancelled(cancelled);
        for (Entry entry : plan.entries) entry.source().validate();
        progress.onProgress(0, plan.totalBytes);
        byte[] buffer = new byte[BUFFER_SIZE];
        long[] done = {0};
        if (plan.format == Format.APK) {
            copy(plan.entries.get(0).source(), output, buffer, cancelled, progress, done, plan.totalBytes);
        } else {
            // APKs are already compressed. Level zero avoids a second expensive compression pass.
            try (ZipOutputStream zip = new ZipOutputStream(new FilterOutputStream(output) {
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    out.write(bytes, offset, length);
                }
                @Override public void close() { /* Caller owns the destination. */ }
            })) {
                zip.setLevel(Deflater.NO_COMPRESSION);
                for (Entry entry : plan.entries) {
                    checkCancelled(cancelled);
                    ZipEntry zipEntry = new ZipEntry(entry.name());
                    zipEntry.setTime(0);
                    zip.putNextEntry(zipEntry);
                    copy(entry.source(), zip, buffer, cancelled, progress, done, plan.totalBytes);
                    zip.closeEntry();
                }
                checkCancelled(cancelled);
                zip.finish();
            }
        }
        checkCancelled(cancelled);
        for (Entry entry : plan.entries) entry.source().validate();
    }

    private static void copy(Source source, OutputStream output, byte[] buffer,
                             BooleanSupplier cancelled, Progress progress, long[] done, long total)
            throws IOException {
        checkCancelled(cancelled);
        source.validate();
        try (FileInputStream input = new FileInputStream(source.file())) {
            long remaining = source.length();
            while (remaining > 0) {
                checkCancelled(cancelled);
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) throw new IOException("安装包读取不完整：" + source.file().getName());
                if (read == 0) continue;
                output.write(buffer, 0, read);
                remaining -= read;
                done[0] += read;
                progress.onProgress(done[0], total);
            }
            checkCancelled(cancelled);
            if (input.read() != -1) throw new IOException("安装包大小已变更，请重试：" + source.file().getName());
        }
        source.validate();
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("提取已取消");
        }
    }

    private static long addLength(long total, long length) {
        try {
            return Math.addExact(total, length);
        } catch (ArithmeticException tooLarge) {
            throw new IllegalArgumentException("安装包总大小过大", tooLarge);
        }
    }

    private static <T> List<T> immutable(List<T> items) {
        ArrayList<T> result = new ArrayList<>(items);
        for (T item : result) Objects.requireNonNull(item);
        return Collections.unmodifiableList(result);
    }

    private static String apkName(String name) {
        String safe = safeName(name);
        return safe.toLowerCase(Locale.ROOT).endsWith(".apk") ? safe : safe + ".apk";
    }

    private static String stemName(App app) {
        return safeName(app.label() + "_" + app.versionName() + "_" + app.packageName());
    }

    /** Retains readable Unicode while removing path components, controls and reserved device names. */
    public static String safeName(String input) {
        StringBuilder result = new StringBuilder();
        input = input == null ? "" : input;
        int count = 0;
        int utf8Bytes = 0;
        for (int i = 0; i < input.length() && count < 64;) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) continue;
            if ("/\\:*?\"<>|".indexOf(cp) >= 0) cp = '_';
            int size = cp <= 0x7f ? 1 : cp <= 0x7ff ? 2 : cp <= 0xffff ? 3 : 4;
            if (utf8Bytes + size > 160) break;
            result.appendCodePoint(cp);
            utf8Bytes += size;
            count++;
        }
        String name = result.toString().trim().replaceAll("^[. ]+|[. ]+$", "");
        if (name.isEmpty()) name = "app";
        String first = name.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (first.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) name = "_" + name;
        return name;
    }

    private static String unique(String name, Set<String> used) {
        String candidate = name;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        for (int number = 2; !used.add(candidate.toLowerCase(Locale.ROOT)); number++) {
            candidate = stem + " (" + number + ")" + suffix;
        }
        return candidate;
    }
}
