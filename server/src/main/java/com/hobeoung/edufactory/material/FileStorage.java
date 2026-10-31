package com.hobeoung.edufactory.material;

import com.hobeoung.edufactory.config.EduProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 업로드 원본 보관 (로컬 디스크). 키 = "materials/<uuid>" */
@Component
public class FileStorage {

  private final Path root;

  public FileStorage(EduProperties props) throws IOException {
    this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
    Files.createDirectories(root);
  }

  public String save(byte[] bytes) throws IOException {
    String key = "materials/" + UUID.randomUUID();
    Path path = resolve(key);
    Files.createDirectories(path.getParent());
    Files.write(path, bytes);
    return key;
  }

  public byte[] read(String key) throws IOException {
    return Files.readAllBytes(resolve(key));
  }

  public void delete(String key) {
    try {
      Files.deleteIfExists(resolve(key));
    } catch (IOException ignored) {
      // 남아도 무해
    }
  }

  private Path resolve(String key) {
    Path p = root.resolve(key).normalize();
    if (!p.startsWith(root)) {
      throw new IllegalArgumentException("잘못된 파일 키");
    }
    return p;
  }
}
