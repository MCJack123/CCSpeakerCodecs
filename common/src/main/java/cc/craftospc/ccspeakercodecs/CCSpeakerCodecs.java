// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs;

import cc.craftospc.ccspeakercodecs.codec.Codec;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public final class CCSpeakerCodecs {
    public static final String MOD_ID = "ccspeakercodecs";
    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    public static class Config {
        private Path filePath = null;
        public Set<String> allowedCodecs = new HashSet<>();

        Config() {
            allowedCodecs.addAll(Arrays.asList("dfpwm", "qoa", "adpcm2", "adpcm3", "adpcm4", "adpcm5"));
        }

        public void load(MinecraftServer server) {
            filePath = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig/" + MOD_ID + ".toml");
            File file = filePath.toFile();
            if (file.exists()) {
                try (FileConfig config = FileConfig.of(file)) {
                    config.load();

                    Optional<String[]> _allowedCodecs = config.getOptional("allowedCodecs");
                    String[] allowedCodecs = _allowedCodecs.orElse(new String[] {"dfpwm", "qoa", "adpcm2", "adpcm3", "adpcm4", "adpcm5"});
                    for (int i = 0; i < allowedCodecs.length; i++) {
                        if ("adpcm".equalsIgnoreCase(allowedCodecs[i])) allowedCodecs[i] = "adpcm4";
                        allowedCodecs[i] = allowedCodecs[i].toLowerCase();
                    }
                    this.allowedCodecs.clear();
                    this.allowedCodecs.add("dfpwm"); // dfpwm is always allowed
                    this.allowedCodecs.addAll(Arrays.asList(allowedCodecs));
                }
            }
        }

        public void save() {
            if (filePath == null) return;
            File file = filePath.toFile();
            try {
                filePath.getParent().toFile().mkdirs();
                if (!file.exists()) file.createNewFile();
                try (FileConfig config = FileConfig.of(file)) {
                    config.set("allowedCodecs", new ArrayList<>(allowedCodecs));
                    config.save();
                }
            } catch (Exception e) {
                LOG.warn(e);
            }
        }
    }

    public static final Config CONFIG = new Config();

    public static void init() {
        // Write common init code here.
        try {Class.forName("dan200.computercraft.shared.peripheral.speaker.DfpwmState");}
        catch (ClassNotFoundException ignore) {}
    }
}
