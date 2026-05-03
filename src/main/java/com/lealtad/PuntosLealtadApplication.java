package com.lealtad;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
@EnableAsync
public class PuntosLealtadApplication {

    public static void main(String[] args) {
        applyDotEnvIfAbsentFromEnvironment();
        SpringApplication.run(PuntosLealtadApplication.class, args);
    }

    /**
     * Lee {@code .env} (subiendo desde {@code user.dir} hasta encontrarlo). No sobrescribe variables que ya
     * vienen del sistema operativo (p. ej. Railway).
     */
    private static void applyDotEnvIfAbsentFromEnvironment() {
        Path dotEnvDir = resolveDotEnvDirectory();
        Dotenv dotenv = Dotenv.configure().directory(dotEnvDir.toString()).ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                return;
            }
            if (System.getenv(key) != null || System.getProperty(key) != null) {
                return;
            }
            System.setProperty(key, entry.getValue());
        });
    }

    private static Path resolveDotEnvDirectory() {
        Path start = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path dir = start;
        for (int depth = 0; depth < 8 && dir != null; depth++) {
            if (Files.isRegularFile(dir.resolve(".env"))) {
                return dir;
            }
            Path parent = dir.getParent();
            if (parent == null || dir.equals(parent)) {
                break;
            }
            dir = parent;
        }
        return start;
    }
}
