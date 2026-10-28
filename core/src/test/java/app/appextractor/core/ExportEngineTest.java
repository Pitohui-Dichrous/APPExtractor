package app.appextractor.core;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ExportEngineTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private ExportEngine.Source source(String entryName, byte[] bytes) throws IOException {
        File file = temp.newFile();
        Files.write(file.toPath(), bytes);
        return new ExportEngine.Source(entryName, file);
    }

    private ExportEngine.App app(String label, ExportEngine.Source... sources) {
        return new ExportEngine.App(label, "app.example", "1.0", List.of(sources));
    }

    private byte[] write(ExportEngine.Plan plan) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExportEngine.write(plan, output, () -> false, (done, total) -> {});
        return output.toByteArray();
    }

    private Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                assertNull("duplicate ZIP path", entries.put(entry.getName(), input.readAllBytes()));
            }
        }
        return entries;
    }

    @Test public void singleApkPreservesEveryByteAndDoesNotCloseDestination() throws Exception {
        byte[] bytes = new byte[180_003];
        new java.util.Random(58).nextBytes(bytes);
        ExportEngine.Plan plan = ExportEngine.plan(List.of(app("示例应用", source("base.apk", bytes))));
        assertEquals(ExportEngine.Format.APK, plan.format());
        assertTrue(plan.filename().endsWith(".apk"));
        assertEquals("application/vnd.android.package-archive", plan.mimeType());
        AtomicBoolean closed = new AtomicBoolean();
        ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override public void close() { closed.set(true); }
        };
        List<Long> progress = new ArrayList<>();
        ExportEngine.write(plan, output, () -> false, (done, total) -> {
            assertEquals(bytes.length, total);
            progress.add(done);
        });
        assertArrayEquals(bytes, output.toByteArray());
        assertFalse(closed.get());
        assertEquals(Long.valueOf(0), progress.get(0));
        assertEquals(Long.valueOf(bytes.length), progress.get(progress.size() - 1));
        for (int i = 1; i < progress.size(); i++) {
            assertTrue(progress.get(i) > progress.get(i - 1));
            assertTrue(progress.get(i) - progress.get(i - 1) <= 64 * 1024);
        }
    }

    @Test public void splitArchiveKeepsAllApksAndSanitizesPaths() throws Exception {
        ExportEngine.Plan plan = ExportEngine.plan(List.of(app("Split",
                source("base.apk", new byte[]{1, 2, 3}),
                source("../config.en.apk", new byte[]{4, 5}),
                source("CONFIG.en.apk", new byte[]{6}),
                source("base.apk", new byte[]{7, 8}))));
        assertEquals(ExportEngine.Format.APKS, plan.format());
        assertTrue(plan.filename().endsWith(".apks"));
        Map<String, byte[]> entries = unzip(write(plan));
        assertEquals(4, entries.size());
        assertArrayEquals(new byte[]{1, 2, 3}, entries.get("base.apk"));
        assertArrayEquals(new byte[]{7, 8}, entries.get("base (2).apk"));
        for (String name : entries.keySet()) {
            assertFalse(name.contains("/"));
            assertFalse(name.contains("\\"));
            assertTrue(name.endsWith(".apk"));
        }
    }

    @Test public void batchDuplicatesNeverOverwriteAndSplitDirectoryIsComplete() throws Exception {
        ExportEngine.App first = app("Same", source("base.apk", new byte[]{1}));
        ExportEngine.App second = app("Same", source("base.apk", new byte[]{2}));
        ExportEngine.App third = app("Same", source("base.apk", new byte[]{3}),
                source("config.arm64.apk", new byte[]{4}));
        ExportEngine.Plan plan = ExportEngine.plan(List.of(first, second, third, third));
        assertEquals(ExportEngine.Format.ZIP, plan.format());
        Map<String, byte[]> entries = unzip(write(plan));
        assertEquals(6, entries.size());
        assertEquals(6, plan.totalBytes());
        assertEquals(4, entries.keySet().stream().filter(name -> name.contains("/")).count());
        assertEquals(2, entries.keySet().stream().filter(name -> name.endsWith("/base.apk")).count());
        assertEquals(2, entries.keySet().stream().filter(name -> name.endsWith("/config.arm64.apk")).count());
    }

    @Test public void cancellationStopsAtNextBufferWithoutClosingCallerStream() throws Exception {
        ExportEngine.Plan plan = ExportEngine.plan(List.of(app("Large", source("base.apk", new byte[300_000]))));
        AtomicBoolean cancel = new AtomicBoolean();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThrows(CancellationException.class, () -> ExportEngine.write(plan, output, cancel::get,
                (done, total) -> { if (done > 0) cancel.set(true); }));
        assertEquals(64 * 1024, output.size());
    }

    @Test public void zipDestinationStaysOpenOnSuccessAndFailure() throws Exception {
        ExportEngine.Plan plan = ExportEngine.plan(List.of(app("Split",
                source("base.apk", new byte[100_000]), source("config.apk", new byte[100]))));
        AtomicBoolean closed = new AtomicBoolean();
        OutputStream failure = new OutputStream() {
            int count;
            @Override public void write(int value) throws IOException {
                if (++count > 200) throw new IOException("disk full");
            }
            @Override public void close() { closed.set(true); }
        };
        assertThrows(IOException.class, () -> ExportEngine.write(plan, failure, () -> false, (d, t) -> {}));
        assertFalse(closed.get());
        ByteArrayOutputStream success = new ByteArrayOutputStream() {
            @Override public void close() { closed.set(true); }
        };
        ExportEngine.write(plan, success, () -> false, (d, t) -> {});
        assertEquals(2, unzip(success.toByteArray()).size());
        assertFalse(closed.get());
    }

    @Test public void sourceChangedBeforeOrDuringCopyIsRejected() throws Exception {
        ExportEngine.Source source = source("base.apk", new byte[100]);
        ExportEngine.Plan plan = ExportEngine.plan(List.of(app("Changed", source)));
        Files.write(source.file().toPath(), new byte[101]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> ExportEngine.write(plan, output, () -> false, (d, t) -> {}));
        assertEquals(0, output.size());

        ExportEngine.Source second = source("base.apk", new byte[200_000]);
        ExportEngine.Plan secondPlan = ExportEngine.plan(List.of(app("Changed during copy", second)));
        assertThrows(IOException.class, () -> ExportEngine.write(secondPlan, new ByteArrayOutputStream(),
                () -> false, (done, total) -> {
                    if (done == 64 * 1024) {
                        try { Files.write(second.file().toPath(), new byte[10]); }
                        catch (IOException e) { throw new RuntimeException(e); }
                    }
                }));
    }

    @Test public void namesPreserveUnicodeAndRejectReservedNamesAndLongPaths() {
        assertEquals("中文应用", ExportEngine.safeName("中文应用"));
        assertEquals("_CON", ExportEngine.safeName("CON"));
        assertEquals("_nul.apk", ExportEngine.safeName("nul.apk"));
        assertEquals("app", ExportEngine.safeName("... "));
        assertEquals("a_b_c", ExportEngine.safeName("a/b\\c"));
        assertFalse(ExportEngine.safeName("a\u202eb").contains("\u202e"));
        assertTrue(ExportEngine.safeName("文件".repeat(200)).getBytes(StandardCharsets.UTF_8).length <= 160);
    }

    @Test public void appAndPlanSnapshotInputLists() throws Exception {
        List<ExportEngine.Source> sources = new ArrayList<>();
        sources.add(source("base.apk", new byte[]{42}));
        ExportEngine.App app = new ExportEngine.App("Immutable", "a.b", "1", sources);
        List<ExportEngine.App> apps = new ArrayList<>(List.of(app));
        ExportEngine.Plan plan = ExportEngine.plan(apps);
        sources.clear();
        apps.clear();
        assertArrayEquals(new byte[]{42}, write(plan));
        assertThrows(UnsupportedOperationException.class, () -> app.sources().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.apps().clear());
    }
}
