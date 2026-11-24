package com.github.rinorsi.cadeditor.client.util.texteditor;

import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyColorCodesTest {
    @Test
    void wrapsOnlySelectedText() {
        assertEquals("before \u00a7cname\u00a7r after",
                LegacyColorCodes.wrap("before name after", 7, 11, "red"));
    }

    @Test
    void wrapsWholeLoreAndPreservesExistingCodes() {
        assertEquals("\u00a76\u00a7lLore\u00a7r",
                LegacyColorCodes.wrap("\u00a7lLore", 0, 6, "gold"));
    }

    @Test
    void mapsEveryVanillaColor() {
        for (ChatFormatting color : ChatFormatting.values()) {
            if (color.isColor()) {
                assertEquals("\u00a7" + color.getChar(), LegacyColorCodes.prefix(color.getName()));
            }
        }
    }

    @Test
    void rejectsNonColorFormats() {
        assertThrows(IllegalArgumentException.class, () -> LegacyColorCodes.prefix("bold"));
        assertThrows(IllegalArgumentException.class, () -> LegacyColorCodes.prefix("#123456"));
    }

    @Test
    void outerColorOverridesInnerColorsAndResets() {
        String text = "A\u00a7cB\u00a7rC\u00a79D\u00a7rE";
        assertEquals("\u00a76ABCDE\u00a7r", LegacyColorCodes.wrap(text, 0, text.length(), "gold"));
    }

    @Test
    void partialSelectionRestoresOriginalColor() {
        assertEquals("\u00a7cA\u00a79BC\u00a7r\u00a7cD\u00a7r",
                LegacyColorCodes.wrap("\u00a7cABCD\u00a7r", 3, 5, "blue"));
    }

    @Test
    void selectionAcrossAdjacentColorsPreservesBothOutsideRegions() {
        assertEquals("\u00a7cA\u00a76BC\u00a7r\u00a79D\u00a7r",
                LegacyColorCodes.wrap("\u00a7cAB\u00a79CD\u00a7r", 3, 7, "gold"));
    }

    @Test
    void reversedSelectionHasTheSameResult() {
        String text = "\u00a7cAB\u00a79CD\u00a7r";
        assertEquals(LegacyColorCodes.recolor(text, 3, 7, "gold").text(),
                LegacyColorCodes.recolor(text, 7, 3, "gold").text());
    }

    @Test
    void splitCodeBoundariesExpandToIncludeWholeCodes() {
        assertEquals("\u00a76AB\u00a7r\u00a79CD",
                LegacyColorCodes.wrap("\u00a7cAB\u00a79CD", 1, 5, "gold"));
    }

    @Test
    void emptySelectionDoesNotInsertCodes() {
        assertEquals("text", LegacyColorCodes.wrap("text", 2, 2, "red"));
    }

    @Test
    void selectionAndStyleOffsetsTrackRemovedCodes() {
        var edit = LegacyColorCodes.recolor("A\u00a7cB\u00a7rC", 0, 7, "gold");
        assertEquals("ABC", edit.text().substring(edit.selectionStart(), edit.selectionEnd()));
        assertEquals(3, edit.mapPosition(3));
        assertEquals(4, edit.mapPosition(6));
        for (int i = 1; i <= 7; i++) {
            assertTrue(edit.mapPosition(i) >= edit.mapPosition(i - 1));
        }
    }

    @Test
    void recoloringTheSameSelectionOverridesThePreviousColor() {
        var first = LegacyColorCodes.recolor("A\u00a7cB\u00a7rC", 0, 7, "gold");
        var second = LegacyColorCodes.recolor(first.text(), first.selectionStart(), first.selectionEnd(), "blue");
        for (Visible character : visible(second.text())) {
            assertEquals(ChatFormatting.BLUE, character.color());
        }
        assertEquals("ABC", second.text().substring(second.selectionStart(), second.selectionEnd()));
    }

    @Test
    void compactsOverriddenColorsAndTrailingCodes() {
        assertEquals("§bName", LegacyColorCodes.compact("§7§bName§r"));
        assertEquals("§cText", LegacyColorCodes.compact("§7§b§cText§7"));
        assertEquals("Text", LegacyColorCodes.compact("Text§r§7"));
    }

    @Test
    void compactPreservesNecessaryStyleTransitions() {
        assertEquals("§c§lBold§r§cPlain", LegacyColorCodes.compact("§c§lBold§r§cPlain"));
        assertEquals("§c§lBold", LegacyColorCodes.compact("§c§l§lBold§7§r"));
        assertEquals("§bText", LegacyColorCodes.compact("§c§l§bText"));
    }

    @Test
    void everySelectionPreservesOutsideColorsAndAllLegacyStyles() {
        String text = "a\u00a7cb\u00a7lc\u00a79d\u00a7oe\u00a7rf\u00a7Ag";
        List<Visible> original = visible(text);
        for (int start = 0; start < text.length(); start++) {
            if (start > 0 && text.charAt(start - 1) == '\u00a7') continue;
            for (int end = start + 1; end <= text.length(); end++) {
                if (text.charAt(end - 1) == '\u00a7') continue;
                List<Visible> edited = visible(LegacyColorCodes.wrap(text, start, end, "gold"));
                assertEquals(original.size(), edited.size());
                for (int i = 0; i < original.size(); i++) {
                    Visible before = original.get(i);
                    Visible after = edited.get(i);
                    String context = "selection " + start + ":" + end + ", character " + i;
                    assertEquals(before.character(), after.character(), context);
                    assertEquals(before.bold(), after.bold(), context);
                    assertEquals(before.italic(), after.italic(), context);
                    assertEquals(before.offset() >= start && before.offset() < end
                            ? ChatFormatting.GOLD : before.color(), after.color(), context);
                }
            }
        }
    }

    private static List<Visible> visible(String text) {
        List<Visible> result = new ArrayList<>();
        ChatFormatting color = null;
        boolean bold = false;
        boolean italic = false;
        for (int i = 0; i < text.length(); i++) {
            ChatFormatting code = text.charAt(i) == '\u00a7' && i + 1 < text.length()
                    ? ChatFormatting.getByCode(text.charAt(i + 1)) : null;
            if (code == null) {
                result.add(new Visible(i, text.charAt(i), color, bold, italic));
            } else {
                i++;
                if (code.isColor() || code == ChatFormatting.RESET) {
                    color = code.isColor() ? code : null;
                    bold = false;
                    italic = false;
                } else if (code == ChatFormatting.BOLD) {
                    bold = true;
                } else if (code == ChatFormatting.ITALIC) {
                    italic = true;
                }
            }
        }
        return result;
    }

    private record Visible(int offset, char character, ChatFormatting color, boolean bold, boolean italic) {
    }
}
