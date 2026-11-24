package com.github.rinorsi.cadeditor.client.screen.model.category.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemDisplayCategoryModelTest {
    @Test
    void namesAreSingleLiteralComponentsWithoutExplicitStyles() {
        var source = Component.literal("").withStyle(style -> style.withItalic(false))
                .append(Component.literal("\u00a7cSword\u00a7r").withStyle(ChatFormatting.BOLD));
        var result = ItemDisplayCategoryModel.plainName(source);
        assertEquals("\u00a7cSword", result.getString());
        assertEquals(Style.EMPTY, result.getStyle());
        assertTrue(result.getSiblings().isEmpty());
    }

    @Test
    void loreUsesGrayCodeInsteadOfComponentStyles() {
        var source = Component.literal("Lore").withStyle(style -> style.withItalic(false)
                .withColor(ChatFormatting.WHITE));
        var result = ItemDisplayCategoryModel.normalizeLoreLine(source);
        assertNotNull(result);
        assertEquals("\u00a77Lore", result.getString());
        assertEquals(Style.EMPTY, result.getStyle());
        assertTrue(result.getSiblings().isEmpty());
    }

    @Test
    void repeatedLoreNormalizationDoesNotDuplicateThePrefix() {
        var first = ItemDisplayCategoryModel.normalizeLoreLine(Component.literal("\u00a7cRed\u00a7r"));
        var second = ItemDisplayCategoryModel.normalizeLoreLine(first);
        assertEquals("\u00a7cRed", second.getString());
        assertEquals(first, second);
    }

    @Test
    void blankLoreIsNotReplacedWithAColorOnlyLine() {
        assertNull(ItemDisplayCategoryModel.normalizeLoreLine(null));
        assertNull(ItemDisplayCategoryModel.normalizeLoreLine(Component.literal("")));
    }

    @Test
    void loreResetsReturnToGrayWithoutChangingOtherColors() {
        var result = ItemDisplayCategoryModel.normalizeLoreLine(
                Component.literal("\u00a7cRed\u00a7rGray\u00a79Blue\u00a7RGray"));
        assertEquals("\u00a7cRed\u00a77Gray\u00a79Blue\u00a77Gray", result.getString());
        assertEquals(Style.EMPTY, result.getStyle());
    }

    @Test
    void namesAndLoreDiscardOverriddenAndTrailingColors() {
        assertEquals("§bName", ItemDisplayCategoryModel.plainName(
                Component.literal("§7§bName§r§7")).getString());
        assertEquals("§bLore", ItemDisplayCategoryModel.normalizeLoreLine(
                Component.literal("§7§bLore§7")).getString());
    }
}
