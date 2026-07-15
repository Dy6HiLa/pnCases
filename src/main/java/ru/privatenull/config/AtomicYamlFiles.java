package ru.privatenull.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/** Crash-safe persistence for user-editable YAML resources. */
final class AtomicYamlFiles {

    private AtomicYamlFiles() {
    }

    static boolean save(FileConfiguration yaml, File target, Logger logger) {
        Path temporary = null;
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("cannot create directory " + parent);
            }
            Path parentPath = parent == null
                    ? target.toPath().toAbsolutePath().getParent()
                    : parent.toPath();
            temporary = Files.createTempFile(parentPath, target.getName() + ".", ".tmp");
            yaml.save(temporary.toFile());

            YamlConfiguration readback = new YamlConfiguration();
            readback.load(temporary.toFile());

            Path targetPath = target.toPath();
            if (Files.isRegularFile(targetPath)) {
                Files.copy(targetPath, targetPath.resolveSibling(target.getName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, targetPath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | InvalidConfigurationException exception) {
            logger.warning("Не удалось безопасно сохранить " + target.getName() + ": " + exception.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
