package vn.haohan.backpack.util;

import vn.haohan.backpack.tier.BackpackTier;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public final class ResourcePackGenerator {
    private ResourcePackGenerator() {}

    private static final List<String> COLORS = List.of(
            "", "black", "blue", "brown", "cyan", "gray", "green",
            "light_blue", "light_gray", "lime", "magenta", "orange",
            "pink", "purple", "red", "white", "yellow"
    );

    public static void generate(File resourcePackDir) {
        File targetTexturesDir = new File(resourcePackDir, "assets/haohan/textures/block");
        File targetModelsDir = new File(resourcePackDir, "assets/haohan/models/item");
        File targetItemsDir = new File(resourcePackDir, "assets/haohan/items");
        targetTexturesDir.mkdirs();
        targetModelsDir.mkdirs();
        targetItemsDir.mkdirs();

        File extItemDir = new File("F:/.HaoHanProject/HaoHan-Resourcepack/assets/haohan/items");
        File extModelDir = new File("F:/.HaoHanProject/HaoHan-Resourcepack/assets/haohan/models/item");
        File extTexDir = new File("F:/.HaoHanProject/HaoHan-Resourcepack/assets/haohan/textures/block");

        // 1. Clean up all old composite textures from both directories
        cleanOldCompositeTextures(targetTexturesDir);
        if (extTexDir.exists()) {
            cleanOldCompositeTextures(extTexDir);
        }

        // 2. Write master 2-layer 3D Blockbench model backpack.json
        writeMaster3DModelJson(new File(targetModelsDir, "backpack.json"));
        if (extModelDir.exists()) {
            writeMaster3DModelJson(new File(extModelDir, "backpack.json"));
        }

        // 3. Load base strap template
        File strapTemplateFile = new File("strap_template.png");
        BufferedImage strapLeatherImg = null;
        try {
            if (strapTemplateFile.exists()) {
                strapLeatherImg = ImageIO.read(strapTemplateFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (strapLeatherImg == null) {
            strapLeatherImg = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        }

        // 4. Save ONLY the 5 Tier Strap Textures (Layer 1)
        for (BackpackTier tier : BackpackTier.values()) {
            BufferedImage strapImg = generateTierStrap(strapLeatherImg, tier);
            String strapFileName = "backpack_strap_" + tier.getId() + ".png";
            try {
                ImageIO.write(strapImg, "PNG", new File(targetTexturesDir, strapFileName));
                if (extTexDir.exists()) {
                    ImageIO.write(strapImg, "PNG", new File(extTexDir, strapFileName));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 5. Save ONLY the 17 Base Textures (Layer 0) and 3D Child Model JSONs
        for (String color : COLORS) {
            String baseFileName = color.isEmpty() ? "backpack.png" : "backpack_" + color + ".png";
            File sourceFile = new File(extTexDir, baseFileName);
            if (!sourceFile.exists()) {
                sourceFile = new File("base_template.png");
            }

            BufferedImage fullBaseImg = null;
            try {
                if (sourceFile.exists()) {
                    fullBaseImg = ImageIO.read(sourceFile);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (fullBaseImg == null) {
                fullBaseImg = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            }

            BufferedImage cleanBase = extractCleanBase(fullBaseImg, strapLeatherImg);
            String cleanBaseName = "backpack_base" + (color.isEmpty() ? "" : "_" + color) + ".png";
            try {
                ImageIO.write(cleanBase, "PNG", new File(targetTexturesDir, cleanBaseName));
                if (extTexDir.exists()) {
                    ImageIO.write(cleanBase, "PNG", new File(extTexDir, cleanBaseName));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Write 3D Child Model JSONs mapping #base and #strap
            for (BackpackTier tier : BackpackTier.values()) {
                String tierModelName = "backpack_" + tier.getId() + (color.isEmpty() ? "" : "_" + color);
                String baseTexPath = "haohan:block/backpack_base" + (color.isEmpty() ? "" : "_" + color);
                String strapTexPath = "haohan:block/backpack_strap_" + tier.getId();

                write3DChildModelJson(targetModelsDir, tierModelName + ".json", baseTexPath, strapTexPath);
                if (extModelDir.exists()) {
                    write3DChildModelJson(extModelDir, tierModelName + ".json", baseTexPath, strapTexPath);
                }

                // Write 1.21.4 Item Definition JSON in assets/haohan/items/
                String modelKey = "haohan:item/" + tierModelName;
                writeItemDefinitionJson(targetItemsDir, tierModelName + ".json", modelKey);
                if (extItemDir.exists()) {
                    writeItemDefinitionJson(extItemDir, tierModelName + ".json", modelKey);
                }
            }
        }

        // 6. Generate GUI reduced slot textures (1-6 rows) and gui.json font
        generateGuiTexturesAndFonts(resourcePackDir);
        File extRpDir = new File("F:/.HaoHanProject/HaoHan-Resourcepack");
        if (extRpDir.exists()) {
            generateGuiTexturesAndFonts(extRpDir);
        }
    }

    private static void generateGuiTexturesAndFonts(File rpDir) {
        File guiTexDir = new File(rpDir, "assets/haohan/textures/gui");
        File fontDir = new File(rpDir, "assets/haohan/font");
        guiTexDir.mkdirs();
        fontDir.mkdirs();

        File masterGuiFile = new File(guiTexDir, "backpack.png");
        if (!masterGuiFile.exists()) {
            File extMaster = new File("F:/.HaoHanProject/HaoHan-Resourcepack/assets/haohan/textures/gui/backpack.png");
            if (extMaster.exists()) {
                masterGuiFile = extMaster;
            }
        }
        if (!masterGuiFile.exists()) return;

        try {
            BufferedImage master = ImageIO.read(masterGuiFile);
            if (master == null) return;

            BufferedImage top = master.getSubimage(0, 0, 176, 17);
            BufferedImage bottom = master.getSubimage(0, 125, 176, 97);

            for (int rows = 1; rows <= 6; rows++) {
                int totalH = 17 + rows * 18 + 97;
                BufferedImage rowImg = new BufferedImage(176, totalH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = rowImg.createGraphics();
                g.drawImage(top, 0, 0, null);
                BufferedImage containerRows = master.getSubimage(0, 17, 176, rows * 18);
                g.drawImage(containerRows, 0, 17, null);
                g.drawImage(bottom, 0, 17 + rows * 18, null);
                g.dispose();

                File outPng = new File(guiTexDir, "backpack_" + rows + ".png");
                ImageIO.write(rowImg, "PNG", outPng);
            }

            // Write gui.json font provider
            String fontJson = """
{
  "providers": [
    {
      "type": "space",
      "advances": {
        "\\uE100": -8
      }
    },
    {
      "type": "bitmap",
      "file": "haohan:gui/backpack.png",
      "ascent": 13,
      "height": 222,
      "chars": [
        "\\uE101"
      ]
    },
    {
      "type": "bitmap",
      "file": "haohan:gui/backpack_1.png",
      "ascent": 13,
      "height": 132,
      "chars": [
        "\\uE102"
      ]
    },
    {
      "type": "bitmap",
      "file": "haohan:gui/backpack_2.png",
      "ascent": 13,
      "height": 150,
      "chars": [
        "\\uE103"
      ]
    },
    {
      "type": "bitmap",
      "file": "haohan:gui/backpack_3.png",
      "ascent": 13,
      "height": 168,
      "chars": [
        "\\uE104"
      ]
    },
    {
      "type": "bitmap",
      "file": "haohan:gui/backpack_4.png",
      "ascent": 13,
      "height": 186,
      "chars": [
        "\\uE105"
      ]
    },
    {
      "type": "bitmap",
      "file": "haohan:gui/backpack_5.png",
      "ascent": 13,
      "height": 204,
      "chars": [
        "\\uE106"
      ]
    },
    {
      "type": "bitmap",
      "file": "haohan:gui/backpack_6.png",
      "ascent": 13,
      "height": 222,
      "chars": [
        "\\uE107"
      ]
    }
  ]
}
""";
            try (FileWriter writer = new FileWriter(new File(fontDir, "gui.json"))) {
                writer.write(fontJson);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void cleanOldCompositeTextures(File dir) {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (name.matches("^backpack_(iron|gold|diamond|netherite|leather)(_.*)?\\.png$")
                    && !name.startsWith("backpack_strap_") && !name.startsWith("backpack_base")) {
                f.delete();
            }
        }
    }

    private static BufferedImage extractCleanBase(BufferedImage source, BufferedImage strapTemplate) {
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage clean = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int srcRgb = source.getRGB(x, y);
                int strapAlpha = (strapTemplate.getRGB(x, y) >> 24) & 0xFF;

                if (strapAlpha > 100) {
                    clean.setRGB(x, y, 0); // transparent under the strap
                } else {
                    clean.setRGB(x, y, srcRgb);
                }
            }
        }
        return clean;
    }

    private static BufferedImage generateTierStrap(BufferedImage baseStrap, BackpackTier tier) {
        int w = baseStrap.getWidth();
        int h = baseStrap.getHeight();
        BufferedImage strap = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        if (tier == BackpackTier.LEATHER) {
            Graphics2D g = strap.createGraphics();
            g.drawImage(baseStrap, 0, 0, null);
            g.dispose();
            return strap;
        }

        Color primary;
        Color highlight;
        Color shadow;

        switch (tier) {
            case IRON -> {
                primary = new Color(205, 212, 220);
                highlight = new Color(248, 250, 253);
                shadow = new Color(135, 145, 158);
            }
            case GOLD -> {
                primary = new Color(246, 200, 35);
                highlight = new Color(255, 242, 125);
                shadow = new Color(185, 135, 15);
            }
            case DIAMOND -> {
                primary = new Color(43, 228, 210);
                highlight = new Color(165, 251, 242);
                shadow = new Color(13, 158, 146);
            }
            case NETHERITE -> {
                primary = new Color(68, 58, 62);
                highlight = new Color(112, 98, 104);
                shadow = new Color(38, 32, 35);
            }
            default -> {
                primary = new Color(255, 255, 255);
                highlight = primary;
                shadow = primary;
            }
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = baseStrap.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                if (alpha == 0) {
                    strap.setRGB(x, y, 0);
                    continue;
                }

                Color c = new Color(rgb);
                int brightness = (c.getRed() + c.getGreen() + c.getBlue()) / 3;

                Color target;
                if (brightness > 175) {
                    target = highlight;
                } else if (brightness > 115) {
                    target = primary;
                } else {
                    target = shadow;
                }

                strap.setRGB(x, y, (alpha << 24) | (target.getRed() << 16) | (target.getGreen() << 8) | target.getBlue());
            }
        }
        return strap;
    }

    private static void write3DChildModelJson(File dir, String filename, String baseTex, String strapTex) {
        String json = "{\n" +
                "  \"parent\": \"haohan:item/backpack\",\n" +
                "  \"textures\": {\n" +
                "    \"base\": \"" + baseTex + "\",\n" +
                "    \"strap\": \"" + strapTex + "\",\n" +
                "    \"particle\": \"" + baseTex + "\"\n" +
                "  }\n" +
                "}\n";
        try (FileWriter writer = new FileWriter(new File(dir, filename))) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeItemDefinitionJson(File dir, String filename, String modelKey) {
        String json = "{\n" +
                "  \"model\": {\n" +
                "    \"type\": \"minecraft:model\",\n" +
                "    \"model\": \"" + modelKey + "\"\n" +
                "  }\n" +
                "}\n";
        try (FileWriter writer = new FileWriter(new File(dir, filename))) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeMaster3DModelJson(File file) {
        String json = """
{
	"format_version": "1.21.11",
	"credit": "Made with Blockbench",
	"textures": {
		"base": "haohan:block/backpack_base",
		"strap": "haohan:block/backpack_strap_leather",
		"particle": "haohan:block/backpack_base"
	},
	"elements": [
		{
			"from": [3, 0, 5],
			"to": [13, 4.5, 13],
			"faces": {
				"north": {"uv": [2, 9.75, 4.5, 11], "texture": "#base"},
				"east": {"uv": [11, 8.25, 13, 9.5], "texture": "#base"},
				"south": {"uv": [7, 9.75, 9.5, 11], "texture": "#base"},
				"west": {"uv": [8.75, 11, 10.75, 12.25], "texture": "#base"},
				"up": {"uv": [5.25, 2, 2.75, 0], "texture": "#base"},
				"down": {"uv": [5.25, 2, 2.75, 4], "texture": "#base"}
			}
		},
		{
			"from": [2.5, 4.5, 5],
			"to": [13.5, 7.5, 13.5],
			"faces": {
				"north": {"uv": [0, 4.5, 2.75, 5.25], "texture": "#base"},
				"east": {"uv": [12, 4.75, 14.25, 5.5], "texture": "#base"},
				"south": {"uv": [11.25, 2.5, 14, 3.25], "texture": "#base"},
				"west": {"uv": [12, 5.5, 14.25, 6.25], "texture": "#base"},
				"up": {"uv": [2.75, 2.25, 0, 0], "texture": "#base"},
				"down": {"uv": [2.75, 2.25, 0, 4.5], "texture": "#base"}
			}
		},
		{
			"from": [2, 3, 6],
			"to": [4, 8, 11],
			"faces": {
				"north": {"uv": [10, 13, 10.5, 14.25], "texture": "#base"},
				"east": {"uv": [5.75, 11.5, 7, 12.75], "texture": "#base"},
				"south": {"uv": [3, 13.25, 3.5, 14.5], "texture": "#base"},
				"west": {"uv": [12, 9.5, 13.25, 10.75], "texture": "#base"},
				"up": {"uv": [13.75, 10.5, 13.25, 9.25], "texture": "#base"},
				"down": {"uv": [11, 13.25, 10.5, 14.5], "texture": "#base"}
			}
		},
		{
			"from": [1.5, 8, 5.5],
			"to": [4.5, 10, 11.5],
			"faces": {
				"north": {"uv": [6.75, 4.75, 7.5, 5.25], "texture": "#base"},
				"east": {"uv": [9.25, 3.25, 10.75, 3.75], "texture": "#base"},
				"south": {"uv": [3.75, 11, 4.5, 11.5], "texture": "#base"},
				"west": {"uv": [12.5, 4, 14, 4.5], "texture": "#base"},
				"up": {"uv": [13.5, 7.75, 12.75, 6.25], "texture": "#base"},
				"down": {"uv": [13.75, 7.75, 13, 9.25], "texture": "#base"}
			}
		},
		{
			"from": [12, 3, 6],
			"to": [14, 8, 11],
			"rotation": {"angle": 0, "axis": "y", "origin": [16, 0, 0]},
			"faces": {
				"north": {"uv": [11, 13.25, 11.5, 14.5], "texture": "#base"},
				"east": {"uv": [10.75, 12, 12, 13.25], "texture": "#base"},
				"south": {"uv": [11.5, 13.25, 12, 14.5], "texture": "#base"},
				"west": {"uv": [12, 12, 13.25, 13.25], "texture": "#base"},
				"up": {"uv": [13.75, 13, 13.25, 11.75], "texture": "#base"},
				"down": {"uv": [12.5, 13.25, 12, 14.5], "texture": "#base"}
			}
		},
		{
			"from": [11.5, 8, 5.5],
			"to": [14.5, 10, 11.5],
			"rotation": {"angle": 0, "axis": "y", "origin": [16, 0, 0]},
			"faces": {
				"north": {"uv": [12, 6.25, 12.75, 6.75], "texture": "#base"},
				"east": {"uv": [0, 13.25, 1.5, 13.75], "texture": "#base"},
				"south": {"uv": [13.5, 1.5, 14.25, 2], "texture": "#base"},
				"west": {"uv": [1.5, 13.25, 3, 13.75], "texture": "#base"},
				"up": {"uv": [9.25, 14.5, 8.5, 13], "texture": "#base"},
				"down": {"uv": [10, 13, 9.25, 14.5], "texture": "#base"}
			}
		},
		{
			"from": [4, 7.5, 5],
			"to": [12, 10.5, 12],
			"faces": {
				"north": {"uv": [8.75, 12.25, 10.75, 13], "texture": "#base"},
				"east": {"uv": [2, 12.5, 3.75, 13.25], "texture": "#base"},
				"south": {"uv": [0, 12.5, 2, 13.25], "texture": "#base"},
				"west": {"uv": [12.5, 3.25, 14.25, 4], "texture": "#base"},
				"up": {"uv": [11.25, 3.25, 9.25, 1.5], "texture": "#base"},
				"down": {"uv": [2, 9.75, 0, 11.5], "texture": "#base"}
			}
		},
		{
			"from": [3.5, 10.5, 5],
			"to": [12.5, 14.5, 12.5],
			"rotation": {"angle": 0, "axis": "y", "origin": [0, -2, 0]},
			"faces": {
				"north": {"uv": [10.75, 11, 13, 12], "texture": "#base"},
				"east": {"uv": [0, 11.5, 2, 12.5], "texture": "#base"},
				"south": {"uv": [11.25, 1.5, 13.5, 2.5], "texture": "#base"},
				"west": {"uv": [3.75, 11.5, 5.75, 12.5], "texture": "#base"},
				"up": {"uv": [2.25, 9.75, 0, 7.75], "texture": "#base"},
				"down": {"uv": [4.5, 7.75, 2.25, 9.75], "texture": "#base"}
			}
		},
		{
			"from": [0, 13, 5],
			"to": [16, 17, 9],
			"rotation": {"angle": -45, "axis": "x", "origin": [7.5, 14, 7]},
			"faces": {
				"north": {"uv": [6.75, 3.75, 10.75, 4.75], "texture": "#base"},
				"east": {"uv": [7, 8.75, 8, 9.75], "texture": "#base"},
				"south": {"uv": [4.5, 7.75, 8.5, 8.75], "texture": "#base"},
				"west": {"uv": [13, 10.75, 14, 11.75], "texture": "#base"},
				"up": {"uv": [12, 5.75, 8, 4.75], "texture": "#base"},
				"down": {"uv": [12, 5.75, 8, 6.75], "texture": "#base"}
			}
		},
		{
			"from": [3, 1.8, 13.8],
			"to": [13, 7.8, 13.8],
			"rotation": {"angle": 0, "axis": "y", "origin": [2.5, 3.5, 13.5]},
			"faces": {
				"north": {"uv": [8.5, 6.75, 11, 8.25], "texture": "#strap"},
				"east": {"uv": [0, 0, 0, 1.5], "texture": "#strap"},
				"south": {"uv": [8.5, 8.25, 11, 9.75], "texture": "#strap"},
				"west": {"uv": [0, 0, 0, 1.5], "texture": "#strap"},
				"up": {"uv": [2.5, 0, 0, 0], "texture": "#strap"},
				"down": {"uv": [2.5, 0, 0, 0], "texture": "#strap"}
			}
		},
		{
			"from": [3, 7.8, 10.8],
			"to": [13, 7.8, 13.8],
			"rotation": {"angle": 0, "axis": "y", "origin": [2.5, 3.5, 13.5]},
			"faces": {
				"north": {"uv": [0, 0, 2.5, 0], "texture": "#strap"},
				"east": {"uv": [0, 0, 0.75, 0], "texture": "#strap"},
				"south": {"uv": [0, 0, 2.5, 0], "texture": "#strap"},
				"west": {"uv": [0, 0, 0.75, 0], "texture": "#strap"},
				"up": {"uv": [14.25, 0.75, 11.75, 0], "texture": "#strap"},
				"down": {"uv": [14.25, 0.75, 11.75, 1.5], "texture": "#strap"}
			}
		},
		{
			"from": [3, 8.8, 12.8],
			"to": [13, 14.8, 12.8],
			"rotation": {"angle": 0, "axis": "y", "origin": [2.5, 3.5, 13.5]},
			"faces": {
				"north": {"uv": [4.5, 8.75, 7, 10.25], "texture": "#strap"},
				"east": {"uv": [0, 0, 0, 1.5], "texture": "#strap"},
				"south": {"uv": [9.25, 0, 11.75, 1.5], "texture": "#strap"},
				"west": {"uv": [0, 0, 0, 1.5], "texture": "#strap"},
				"up": {"uv": [2.5, 0, 0, 0], "texture": "#strap"},
				"down": {"uv": [2.5, 0, 0, 0], "texture": "#strap"}
			}
		},
		{
			"from": [3, 14.8, 7.8],
			"to": [13, 14.8, 12.8],
			"rotation": {"angle": 0, "axis": "y", "origin": [2.5, 3.5, 13.5]},
			"faces": {
				"north": {"uv": [0, 0, 2.5, 0], "texture": "#strap"},
				"east": {"uv": [0, 0, 1.25, 0], "texture": "#strap"},
				"south": {"uv": [0, 0, 2.5, 0], "texture": "#strap"},
				"west": {"uv": [0, 0, 1.25, 0], "texture": "#strap"},
				"up": {"uv": [12, 11, 9.5, 9.75], "texture": "#strap"},
				"down": {"uv": [7, 10.25, 4.5, 11.5], "texture": "#strap"}
			}
		},
		{
			"from": [1.2, 4.3, 5],
			"to": [1.2, 10.3, 12],
			"rotation": {"angle": 0, "axis": "y", "origin": [1.5, 7, 11.5]},
			"faces": {
				"north": {"uv": [0, 0, 0, 1.5], "texture": "#strap"},
				"east": {"uv": [10.75, 3.25, 12.5, 4.75], "texture": "#strap"},
				"south": {"uv": [0, 0, 0, 1.5], "texture": "#strap"},
				"west": {"uv": [2, 11, 3.75, 12.5], "texture": "#strap"},
				"up": {"uv": [0, 1.75, 0, 0], "texture": "#strap"},
				"down": {"uv": [0, 0, 0, 1.75], "texture": "#strap"}
			}
		},
		{
			"from": [1.2, 10.3, 5],
			"to": [4.2, 10.3, 12],
			"rotation": {"angle": 0, "axis": "y", "origin": [1.5, 7, 11.5]},
			"faces": {
				"north": {"uv": [0, 0, 0.75, 0], "texture": "#strap"},
				"east": {"uv": [0, 0, 1.75, 0], "texture": "#strap"},
				"south": {"uv": [0, 0, 0.75, 0], "texture": "#strap"},
				"west": {"uv": [0, 0, 1.75, 0], "texture": "#strap"},
				"up": {"uv": [4.5, 14.25, 3.75, 12.5], "texture": "#strap"},
				"down": {"uv": [5.25, 12.5, 4.5, 14.25], "texture": "#strap"}
			}
		},
		{
			"from": [14.8, 4.3, 5],
			"to": [14.8, 10.3, 12],
			"rotation": {"angle": 0, "axis": "y", "origin": [14.5, 7, 11.5]},
			"faces": {
				"north": {"uv": [0, 0, 0, 1.5], "texture": "#strap"},
				"east": {"uv": [11, 6.75, 12.75, 8.25], "texture": "#strap"},
				"south": {"uv": [0, 0, 0, 1.5], "texture": "#strap"},
				"west": {"uv": [7, 11, 8.75, 12.5], "texture": "#strap"},
				"up": {"uv": [0, 1.75, 0, 0], "texture": "#strap"},
				"down": {"uv": [0, 0, 0, 1.75], "texture": "#strap"}
			}
		},
		{
			"from": [11.8, 10.3, 5],
			"to": [14.8, 10.3, 12],
			"rotation": {"angle": 0, "axis": "y", "origin": [14.5, 7, 11.5]},
			"faces": {
				"north": {"uv": [0, 0, 0.75, 0], "texture": "#strap"},
				"east": {"uv": [0, 0, 1.75, 0], "texture": "#strap"},
				"south": {"uv": [0, 0, 0.75, 0], "texture": "#strap"},
				"west": {"uv": [0, 0, 1.75, 0], "texture": "#strap"},
				"up": {"uv": [6, 14.5, 5.25, 12.75], "texture": "#strap"},
				"down": {"uv": [6.75, 12.75, 6, 14.5], "texture": "#strap"}
			}
		},
		{
			"from": [1.2, 10.3, 5],
			"to": [4.2, 10.3, 12],
			"rotation": {"angle": 0, "axis": "y", "origin": [1.5, 7, 11.5]},
			"faces": {
				"north": {"uv": [0, 0, 0.75, 0], "texture": "#strap"},
				"east": {"uv": [0, 0, 1.75, 0], "texture": "#strap"},
				"south": {"uv": [0, 0, 0.75, 0], "texture": "#strap"},
				"west": {"uv": [0, 0, 1.75, 0], "texture": "#strap"},
				"up": {"uv": [7.75, 14.25, 7, 12.5], "texture": "#strap"},
				"down": {"uv": [8.5, 12.5, 7.75, 14.25], "texture": "#strap"}
			}
		},
		{
			"from": [3.5, 3, 14],
			"to": [12.5, 4, 14],
			"faces": {
				"north": {"uv": [12.5, 4.5, 14.75, 4.75], "texture": "#strap"},
				"east": {"uv": [0, 0, 0, 0.25], "texture": "#strap"},
				"south": {"uv": [13.25, 10.5, 15.5, 10.75], "texture": "#strap"},
				"west": {"uv": [0, 0, 0, 0.25], "texture": "#strap"},
				"up": {"uv": [2.25, 0, 0, 0], "texture": "#strap"},
				"down": {"uv": [2.25, 0, 0, 0], "texture": "#strap"}
			}
		},
		{
			"from": [4.5, 9.8, 13],
			"to": [11.5, 10.8, 13],
			"faces": {
				"north": {"uv": [12.5, 13.25, 14.25, 13.5], "texture": "#strap"},
				"east": {"uv": [0, 0, 0, 0.25], "texture": "#strap"},
				"south": {"uv": [13.25, 13, 15, 13.25], "texture": "#strap"},
				"west": {"uv": [0, 0, 0, 0.25], "texture": "#strap"},
				"up": {"uv": [1.75, 0, 0, 0], "texture": "#strap"},
				"down": {"uv": [1.75, 0, 0, 0], "texture": "#strap"}
			}
		},
		{
			"from": [15, 6, 5.5],
			"to": [15, 7, 11.5],
			"faces": {
				"north": {"uv": [0, 0, 0, 0.25], "texture": "#strap"},
				"east": {"uv": [5.25, 3.75, 6.75, 4], "texture": "#strap"},
				"south": {"uv": [0, 0, 0, 0.25], "texture": "#strap"},
				"west": {"uv": [13.5, 2, 15, 2.25], "texture": "#strap"},
				"up": {"uv": [0, 1.5, 0, 0], "texture": "#strap"},
				"down": {"uv": [0, 0, 0, 1.5], "texture": "#strap"}
			}
		},
		{
			"from": [1, 6, 5.5],
			"to": [1, 7, 11.5],
			"rotation": {"angle": 0, "axis": "y", "origin": [16, 0, 0]},
			"faces": {
				"north": {"uv": [0, 0, 0, 0.25], "texture": "#strap"},
				"east": {"uv": [13.5, 2.25, 15, 2.5], "texture": "#strap"},
				"south": {"uv": [0, 0, 0, 0.25], "texture": "#strap"},
				"west": {"uv": [13.5, 6.25, 15, 6.5], "texture": "#strap"},
				"up": {"uv": [0, 1.5, 0, 0], "texture": "#strap"},
				"down": {"uv": [0, 0, 0, 1.5], "texture": "#strap"}
			}
		},
		{
			"from": [3, 12.4, 3.8],
			"to": [4, 16.8, 8.2],
			"rotation": {"angle": -45, "axis": "x", "origin": [3, 15, 6]},
			"faces": {
				"north": {"uv": [0, 5.25, 0.25, 6.5], "texture": "#strap"},
				"east": {"uv": [0, 0, 2.9, 2.15], "texture": "#strap"},
				"south": {"uv": [0, 5.25, 0.25, 6.5], "texture": "#strap"},
				"west": {"uv": [0, 0, 2.9, 2.15], "texture": "#strap"},
				"up": {"uv": [0, 5.25, 0.25, 6.5], "texture": "#strap"},
				"down": {"uv": [0, 5.25, 0.25, 6.5], "texture": "#strap"}
			}
		},
		{
			"from": [12, 12.4, 3.8],
			"to": [13, 16.8, 8.2],
			"rotation": {"angle": -45, "axis": "x", "origin": [12, 15, 6]},
			"faces": {
				"north": {"uv": [0, 5.25, 0.25, 6.5], "texture": "#strap"},
				"east": {"uv": [0, 0, 2.9, 2.15], "texture": "#strap"},
				"south": {"uv": [0, 5.25, 0.25, 6.5], "texture": "#strap"},
				"west": {"uv": [0, 0, 2.9, 2.15], "texture": "#strap"},
				"up": {"uv": [0, 5.25, 0.25, 6.5], "texture": "#strap"},
				"down": {"uv": [0, 5.25, 0.25, 6.5], "texture": "#strap"}
			}
		}
	],
	"display": {
		"thirdperson_righthand": {
			"rotation": [75, -180, 0],
			"translation": [0, 1, 0],
			"scale": [0.375, 0.375, 0.375]
		},
		"thirdperson_lefthand": {
			"rotation": [75, -180, 0],
			"translation": [0, 2.5, 0],
			"scale": [0.375, 0.375, 0.375]
		},
		"firstperson_righthand": {
			"rotation": [0, -170, 0],
			"scale": [0.35, 0.35, 0.35]
		},
		"firstperson_lefthand": {
			"rotation": [0, 135, 0],
			"scale": [0.35, 0.35, 0.35]
		},
		"ground": {
			"translation": [0, 3, 0],
			"scale": [0.85, 0.85, 0.85]
		},
		"gui": {
			"rotation": [11.65, 54.93, -3.22],
			"scale": [0.625, 0.625, 0.625]
		},
		"head": {
			"translation": [0, -60, 8],
			"scale": [1.61, 1.61, 1.61]
		},
		"fixed": {
			"scale": [0.5, 0.5, 0.5]
		}
	}
}
""";
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
