package io.prodaccess.runner.sandbox;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.io.FileSystem;

/** Read-only in-memory module store: the only files the guest can import. Never touches the real disk. */
final class VirtualFileSystem implements FileSystem {
    private final Map<String, String> files;

    VirtualFileSystem(Map<String, String> files) {
        this.files = files;
    }

    @Override public Path parsePath(URI uri) { return Path.of(uri.getPath()); }
    @Override public Path parsePath(String path) { return Path.of(path); }
    @Override public Path toAbsolutePath(Path path) { return Path.of("/").resolve(path).normalize(); }
    @Override public Path toRealPath(Path path, LinkOption... options) { return toAbsolutePath(path); }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... options) throws IOException {
        if (modes.contains(AccessMode.WRITE) || modes.contains(AccessMode.EXECUTE) || !files.containsKey(key(path))) {
            throw new NoSuchFileException(path.toString());
        }
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        String src = files.get(key(path));
        if (src == null || options.stream().anyMatch(o -> o != java.nio.file.StandardOpenOption.READ)) {
            throw new NoSuchFileException(path.toString());
        }
        byte[] bytes = src.getBytes(StandardCharsets.UTF_8);
        return new java.nio.channels.SeekableByteChannel() {
            long pos;
            @Override public int read(java.nio.ByteBuffer dst) {
                if (pos >= bytes.length) return -1;
                int n = (int) Math.min(dst.remaining(), bytes.length - pos);
                dst.put(bytes, (int) pos, n);
                pos += n;
                return n;
            }
            @Override public int write(java.nio.ByteBuffer src) { throw new UnsupportedOperationException(); }
            @Override public long position() { return pos; }
            @Override public java.nio.channels.SeekableByteChannel position(long p) { pos = p; return this; }
            @Override public long size() { return bytes.length; }
            @Override public java.nio.channels.SeekableByteChannel truncate(long size) { throw new UnsupportedOperationException(); }
            @Override public boolean isOpen() { return true; }
            @Override public void close() {}
        };
    }

    @Override public void createDirectory(Path dir, FileAttribute<?>... attrs) { throw new SecurityException("read-only"); }
    @Override public void delete(Path path) { throw new SecurityException("read-only"); }
    @Override public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        throw new NoSuchFileException(dir.toString());
    }
    @Override public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        if (!files.containsKey(key(path))) throw new NoSuchFileException(path.toString());
        return Map.of("isRegularFile", true, "isDirectory", false, "size", (long) files.get(key(path)).length());
    }

    private static String key(Path p) {
        return Path.of("/").resolve(p).normalize().toString();
    }
}
