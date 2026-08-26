package vn.haohan.backpack.util;

import java.io.File;

public final class ResourcePackRunner {
    public static void main(String[] args) {
        File out = new File("resourcepack");
        ResourcePackGenerator.generate(out);
        System.out.println("Generated ResourcePack into: " + out.getAbsolutePath());
    }
}
