package tools;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

public class TextureGenerator {
    public static final Object[][] DYES = {
        {"white", new Color(249, 255, 254)},
        {"orange", new Color(249, 128, 29)},
        {"magenta", new Color(199, 78, 189)},
        {"light_blue", new Color(58, 179, 218)},
        {"yellow", new Color(254, 216, 61)},
        {"lime", new Color(128, 199, 31)},
        {"pink", new Color(243, 139, 170)},
        {"gray", new Color(71, 79, 82)},
        {"light_gray", new Color(157, 157, 151)},
        {"cyan", new Color(22, 156, 156)},
        {"purple", new Color(137, 50, 184)},
        {"blue", new Color(60, 68, 170)},
        {"brown", new Color(131, 84, 50)},
        {"green", new Color(94, 124, 22)},
        {"red", new Color(176, 46, 38)},
        {"black", new Color(29, 29, 33)}
    };

    public static BufferedImage shiftHueImage(BufferedImage source, int targetRgb) {
        if (source == null) return null;
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        float[] targetHsb = Color.RGBtoHSB((targetRgb >> 16) & 0xFF, (targetRgb >> 8) & 0xFF, targetRgb & 0xFF, null);
        float targetHue = targetHsb[0];
        float targetSat = targetHsb[1];
        float targetBri = targetHsb[2];

        float[] pixelHsb = new float[3];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    result.setRGB(x, y, argb);
                    continue;
                }

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                Color.RGBtoHSB(r, g, b, pixelHsb);

                float newHue = targetHue;
                float newSat = Math.min(1.0f, Math.max(0.10f, pixelHsb[1] * (targetSat > 0.05f ? (0.6f + 0.4f * targetSat) : targetSat)));
                float newBri = Math.min(1.0f, Math.max(0.0f, pixelHsb[2] * (0.35f + 0.65f * targetBri)));

                int newRgb = Color.HSBtoRGB(newHue, newSat, newBri);
                int newArgb = (alpha << 24) | (newRgb & 0x00FFFFFF);
                result.setRGB(x, y, newArgb);
            }
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        File rpDir = new File("F:/.HaoHanProject/HaoHan-Resourcepack");
        File texturesDir = new File(rpDir, "assets/haohan/textures/block");
        File modelsDir = new File(rpDir, "assets/haohan/models/item");
        File itemsDir = new File(rpDir, "assets/haohan/items");
        File blockModelsDir = new File(rpDir, "assets/haohan/models/block");

        texturesDir.mkdirs(); modelsDir.mkdirs(); itemsDir.mkdirs(); blockModelsDir.mkdirs();

        File srcFile = new File(texturesDir, "backpack.png");
        if (!srcFile.exists()) {
            srcFile = new File("src/main/resources/backpack.png");
        }
        BufferedImage base = ImageIO.read(srcFile);

        // Read base model json templates
        File baseModelFile = new File(modelsDir, "backpack.json");
        String baseModelContent = Files.readString(baseModelFile.toPath());

        File baseFpModelFile = new File(modelsDir, "backpack_fp.json");
        String baseFpModelContent = baseFpModelFile.exists() ? Files.readString(baseFpModelFile.toPath()) : null;

        File normalMap = new File(texturesDir, "backpack_n.png");
        File specularMap = new File(texturesDir, "backpack_s.png");

        // Ensure default backpack.json in items uses valid 1.21 format
        File defaultItemFile = new File(itemsDir, "backpack.json");
        String defaultItemJson = "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"haohan:item/backpack\"\n  }\n}";
        Files.writeString(defaultItemFile.toPath(), defaultItemJson);

        for (Object[] entry : DYES) {
            String colorName = (String) entry[0];
            Color color = (Color) entry[1];
            int rgb = color.getRGB();

            // 1. Generate & save hue shifted texture
            BufferedImage shifted = shiftHueImage(base, rgb);
            File texFile = new File(texturesDir, "backpack_" + colorName + ".png");
            ImageIO.write(shifted, "PNG", texFile);

            // Copy normal & specular maps if present
            if (normalMap.exists()) {
                File normOut = new File(texturesDir, "backpack_" + colorName + "_n.png");
                Files.copy(normalMap.toPath(), normOut.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (specularMap.exists()) {
                File specOut = new File(texturesDir, "backpack_" + colorName + "_s.png");
                Files.copy(specularMap.toPath(), specOut.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // 2. Generate full standalone model (item)
            String coloredModelJson = baseModelContent.replace("\"haohan:block/backpack\"", "\"haohan:block/backpack_" + colorName + "\"");
            File modelFile = new File(modelsDir, "backpack_" + colorName + ".json");
            Files.writeString(modelFile.toPath(), coloredModelJson);

            // 3. Generate first-person model if exists
            if (baseFpModelContent != null) {
                String coloredFpModelJson = baseFpModelContent.replace("\"haohan:block/backpack\"", "\"haohan:block/backpack_" + colorName + "\"");
                File fpModelFile = new File(modelsDir, "backpack_fp_" + colorName + ".json");
                Files.writeString(fpModelFile.toPath(), coloredFpModelJson);

                File fpItemFile = new File(itemsDir, "backpack_fp_" + colorName + ".json");
                String fpItemJson = "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"haohan:item/backpack_fp_" + colorName + "\"\n  }\n}";
                Files.writeString(fpItemFile.toPath(), fpItemJson);
            }

            // 4. Generate 1.21.4 item definition
            File itemFile = new File(itemsDir, "backpack_" + colorName + ".json");
            String itemJson = "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"haohan:item/backpack_" + colorName + "\"\n  }\n}";
            Files.writeString(itemFile.toPath(), itemJson);

            // 5. Generate block model
            File blockModelFile = new File(blockModelsDir, "backpack_" + colorName + ".json");
            String blockModelJson = "{\n  \"parent\": \"haohan:item/backpack_" + colorName + "\"\n}";
            Files.writeString(blockModelFile.toPath(), blockModelJson);

            System.out.println("Generated all assets for: backpack_" + colorName);
        }

        System.out.println("\nSUCCESS: All 16 dyed backpack assets generated completely in " + rpDir.getPath());
    }
}
