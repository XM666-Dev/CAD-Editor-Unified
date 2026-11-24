package com.github.rinorsi.cadeditor.client.screen.model;

import com.github.franckyi.databindings.base.DataBindingsImpl;
import com.github.franckyi.guapi.api.Guapi;
import com.github.franckyi.guapi.base.NodeFactoryImpl;
import com.github.rinorsi.cadeditor.client.context.ItemEditorContext;
import com.github.rinorsi.cadeditor.client.screen.controller.entry.TextEntryController;
import com.github.rinorsi.cadeditor.client.screen.model.category.item.ItemDisplayCategoryModel;
import com.github.rinorsi.cadeditor.client.screen.model.category.item.ItemAttributeModifiersCategoryModel;
import com.github.rinorsi.cadeditor.client.screen.model.category.item.ItemGeneralCategoryModel;
import com.github.rinorsi.cadeditor.client.screen.model.entry.IntegerEntryModel;
import com.github.rinorsi.cadeditor.client.screen.model.entry.item.AttributeModifierEntryModel;
import com.github.rinorsi.cadeditor.client.screen.model.entry.TextEntryModel;
import com.github.rinorsi.cadeditor.client.screen.view.entry.TextEntryView;
import com.github.rinorsi.cadeditor.client.util.texteditor.StyleType;
import com.github.rinorsi.cadeditor.client.util.texteditor.TextEditorActionHandler;
import com.github.rinorsi.cadeditor.client.util.texteditor.LegacyColorCodes;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.CustomModelData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ItemEditorModelDisplayTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        DataBindingsImpl.init();
        Guapi.setNodeFactory(NodeFactoryImpl.INSTANCE);
    }

    private static final RegistryAccess.Frozen REGISTRY_ACCESS =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).freeze();

    @Test
    void clearingCustomModelDataDoesNotRestoreTheOldSnapshot() {
        var stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(123));
        var model = createEditor(stack);
        var general = model.getCategories().stream()
                .filter(ItemGeneralCategoryModel.class::isInstance).findFirst().orElseThrow();
        ((IntegerEntryModel) general.getEntries().get(7)).setValue(0);

        for (int save = 0; save < 2; save++) {
            model.apply();
            model.getContext().update();
            assertNull(model.getContext().getItemStack().get(DataComponents.CUSTOM_MODEL_DATA));
            assertFalse(model.getContext().getTag().getCompound("components").contains("minecraft:custom_model_data"));
        }
    }

    @Test
    void largeCountsStillProduceASerializableSave() {
        for (int count : List.of(65, 99, 100, 999)) {
            var model = createEditor(new ItemStack(Items.STICK));
            var general = model.getCategories().stream()
                    .filter(ItemGeneralCategoryModel.class::isInstance).findFirst().orElseThrow();
            ((IntegerEntryModel) general.getEntries().get(1)).setValue(count);

            model.apply();
            model.getContext().update();

            var decoded = ItemStack.parseOptional(REGISTRY_ACCESS, model.getContext().getTag());
            assertFalse(decoded.isEmpty());
            assertEquals(Math.min(count, Item.ABSOLUTE_MAX_STACK_SIZE), decoded.getCount());
            assertTrue(decoded.getMaxStackSize() <= Item.ABSOLUTE_MAX_STACK_SIZE);
        }
    }

    @Test
    void defaultItemNameIsNotSavedAsAnOverride() {
        for (var item : List.of(Items.STICK, Items.DIAMOND_SWORD)) {
            var model = createEditor(new ItemStack(item));
            for (int save = 0; save < 2; save++) {
                model.apply();
                assertNoItemNameOverride(model);
            }
        }
    }

    @Test
    void restoringDefaultOrBlankItemNameRemovesExistingOverride() {
        for (String name : List.of("", Component.translatable(Items.STICK.getDescriptionId()).getString())) {
            var stack = new ItemStack(Items.STICK);
            stack.set(DataComponents.ITEM_NAME, Component.literal("Custom item name"));
            var model = createEditor(stack);
            var entry = (TextEntryModel) displayCategory(model).getEntries().getFirst();
            entry.setValue(Component.literal(name));
            for (int save = 0; save < 2; save++) {
                model.apply();
                assertNoItemNameOverride(model);
            }
        }
    }

    @Test
    void customItemNameStillPersists() {
        var model = createEditor(new ItemStack(Items.STICK));
        var entry = (TextEntryModel) displayCategory(model).getEntries().getFirst();
        entry.setValue(Component.literal("\u00a7cCustom item name"));
        for (int save = 0; save < 2; save++) {
            model.apply();
            var decoded = ItemStack.parseOptional(REGISTRY_ACCESS, model.getContext().getTag());
            assertEquals("\u00a7cCustom item name", decoded.get(DataComponents.ITEM_NAME).getString());
        }
    }

    private static void assertNoItemNameOverride(ItemEditorModel model) {
        var components = model.getContext().getTag().getCompound("components");
        assertFalse(components.contains("minecraft:item_name"));
        assertFalse(components.contains("!minecraft:item_name"));
        assertNull(model.getContext().getItemStack().get(DataComponents.ITEM_NAME));
    }

    @Test
    void unrelatedEditsDoNotWriteLoreOrAttributeOverrides() {
        for (var item : List.of(Items.STICK, Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_CHESTPLATE)) {
            ItemStack stack = new ItemStack(item);
            var originalModifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
            var model = createEditor(stack);
            var name = (TextEntryModel) displayCategory(model).getEntries().get(1);
            name.setValue(Component.literal("Edited name"));

            for (int save = 0; save != 2; save++) {
                model.apply();
                var components = model.getContext().getTag().getCompound("components");
                assertFalse(components.contains("minecraft:attribute_modifiers"), item.toString());
                assertFalse(components.contains("!minecraft:attribute_modifiers"), item.toString());
                assertFalse(components.contains("!minecraft:lore"), item.toString());
                assertEquals(originalModifiers, model.getContext().getItemStack().get(DataComponents.ATTRIBUTE_MODIFIERS));
            }
        }
    }

    @Test
    void deletingExistingLoreStillPersists() {
        var model = createEditor();
        displayCategory(model).deleteEntry(0);

        model.apply();
        model.apply();

        var lore = ItemStack.parseOptional(REGISTRY_ACCESS, model.getContext().getTag()).get(DataComponents.LORE);
        assertTrue(lore == null || lore.lines().isEmpty());
    }

    @Test
    void deletingDefaultModifiersStillPersists() {
        var model = createEditor(new ItemStack(Items.DIAMOND_SWORD));
        var attributes = model.getCategories().stream()
                .filter(ItemAttributeModifiersCategoryModel.class::isInstance)
                .map(ItemAttributeModifiersCategoryModel.class::cast).findFirst().orElseThrow();
        while (attributes.getEntries().getFirst() instanceof AttributeModifierEntryModel) {
            attributes.deleteEntry(0);
        }

        model.apply();
        model.apply();

        assertTrue(model.getContext().getTag().getCompound("components").contains("!minecraft:attribute_modifiers"));
        assertNull(model.getContext().getItemStack().get(DataComponents.ATTRIBUTE_MODIFIERS));
    }

    @Test
    void editingSwordAttributesRetainsVanillaBaseModifierIds() {
        var model = createEditor(new ItemStack(Items.DIAMOND_SWORD));
        var attributes = model.getCategories().stream()
                .filter(ItemAttributeModifiersCategoryModel.class::isInstance)
                .map(ItemAttributeModifiersCategoryModel.class::cast).findFirst().orElseThrow();
        var damage = attributes.getEntries().stream()
                .filter(AttributeModifierEntryModel.class::isInstance)
                .map(AttributeModifierEntryModel.class::cast)
                .filter(entry -> entry.getAttributeName().equals("minecraft:generic.attack_damage"))
                .findFirst().orElseThrow();

        damage.setAmount(10.0);
        model.apply();

        var modifiers = model.getContext().getItemStack().get(DataComponents.ATTRIBUTE_MODIFIERS);
        assertNotNull(modifiers);
        assertTrue(modifiers.modifiers().stream().anyMatch(entry ->
                entry.modifier().id().toString().equals("minecraft:base_attack_damage")
                        && entry.modifier().amount() == 10.0));
        assertTrue(modifiers.modifiers().stream().anyMatch(entry ->
                entry.modifier().id().toString().equals("minecraft:base_attack_speed")));
        assertEquals(2, modifiers.modifiers().size(), "editing base values must not add extra modifiers");
    }

    @Test
    void savingLoreDoesNotReadBackAStaleTextField() {
        var model = createEditor();
        var display = displayCategory(model);
        var lore = (TextEntryModel) display.getEntries().get(display.getEntryListStart());
        var staleText = lore.getValue().copy();
        lore.setOnApply(() -> lore.setValue(staleText.copy()));
        String edited = "\u00a77\u00a7cRed lore\u00a77";
        lore.setValue(Component.literal(edited));

        model.apply();

        assertSavedLore(model, "§cRed lore");
        assertEquals(edited, lore.getValue().getString());
    }

    @Test
    void successiveLoreEditsSurviveRepeatedSaves() {
        var model = createEditor();
        var display = displayCategory(model);
        var lore = (TextEntryModel) display.getEntries().get(display.getEntryListStart());
        lore.setOnApply(() -> lore.setValue(Component.literal("Old lore")));
        for (String color : List.of("\u00a7c", "\u00a79", "\u00a76")) {
            String edited = "\u00a77" + color + "Updated lore\u00a77";
            lore.setValue(Component.literal(edited));
            model.apply();
            assertSavedLore(model, color + "Updated lore");
        }
    }

    @Test
    void doneCommitsTheFocusedLoreEditorBeforeSaving() {
        var model = createEditor();
        var display = displayCategory(model);
        var lore = (TextEntryModel) display.getEntries().get(display.getEntryListStart());
        String edited = "\u00a77Last focused edit";
        model.setActiveTextEditor(new TextEditorActionHandler() {
            @Override
            public void commitPendingEdit() {
                lore.setValue(Component.literal(edited));
            }

            @Override public void removeColorFormatting() {}
            @Override public void addColorFormatting(String color) {}
            @Override public void addStyleFormatting(StyleType type) {}
        });

        model.apply();
        model.getContext().update();

        assertSavedLore(model, edited);
    }

    @Test
    void recoloringLoreSurvivesClosingAndOpeningTheEditorAgain() {
        var firstEditor = createEditor(new ItemStack(Items.IRON_SWORD));
        var firstDisplay = displayCategory(firstEditor);
        firstDisplay.addEntryInList();
        var addedLore = (TextEntryModel) firstDisplay.getEntries().get(firstDisplay.getEntryListStart());
        addedLore.setValue(Component.literal("First lore"));
        firstEditor.apply();
        firstEditor.getContext().update();
        ItemStack afterFirstSave = firstEditor.getContext().getItemStack().copy();
        assertEquals("§7First lore", afterFirstSave.get(DataComponents.LORE).lines().getFirst().getString());

        var secondEditor = createEditor(afterFirstSave);
        var secondDisplay = displayCategory(secondEditor);
        var existingLore = (TextEntryModel) secondDisplay.getEntries().get(secondDisplay.getEntryListStart());
        String original = existingLore.getValue().getString();
        String recolored = LegacyColorCodes.wrap(original, 2, original.length(), "red")
                .replace("§r", "§7");
        existingLore.setValue(Component.literal(recolored));
        secondEditor.apply();
        secondEditor.getContext().update();

        assertSavedLore(secondEditor, "§cFirst lore");
        var thirdEditor = createEditor(secondEditor.getContext().getItemStack().copy());
        var reopenedLore = (TextEntryModel) displayCategory(thirdEditor).getEntries()
                .get(displayCategory(thirdEditor).getEntryListStart());
        assertEquals("§cFirst lore", reopenedLore.getValue().getString());
    }

    @Test
    void recoloringExistingLoreThroughControllerIsCommittedOnDone() {
        var stack = new ItemStack(Items.IRON_SWORD);
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("§7First lore"))));
        var editor = createEditor(stack);
        var display = displayCategory(editor);
        var lore = (TextEntryModel) display.getEntries().get(display.getEntryListStart());
        var view = new TextEntryView();
        view.build();
        var controller = new TextEntryController(lore, view);
        controller.bind();
        view.getTextField().setCursorPosition(2);
        view.getTextField().setHighlightPosition(view.getTextField().getText().length());
        editor.setActiveTextEditor(controller);

        controller.addColorFormatting("red");
        assertEquals("§cFirst lore",
                editor.getContext().getItemStack().get(DataComponents.LORE).lines().getFirst().getString());
        editor.apply();
        editor.getContext().update();

        assertSavedLore(editor, "§cFirst lore");
    }

    @Test
    void richTextEntriesStillFlushTheirFormattingOnApply() {
        var model = createEditor();
        var applied = new AtomicReference<Component>();
        var entry = new TextEntryModel(displayCategory(model), Component.literal("Text"),
                Component.literal("Before"), applied::set);
        entry.setOnApply(() -> entry.setValue(Component.literal("After")));

        entry.apply();

        assertNotNull(applied.get());
        assertEquals("After", applied.get().getString());
    }

    private static TestItemEditorModel createEditor() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Old lore"))));
        return createEditor(stack);
    }

    private static TestItemEditorModel createEditor(ItemStack stack) {
        var context = new ItemEditorContext(stack, null, true, ignored -> {});
        var model = new TestItemEditorModel(context);
        model.initalize();
        return model;
    }

    private static ItemDisplayCategoryModel displayCategory(ItemEditorModel model) {
        return model.getCategories().stream()
                .filter(ItemDisplayCategoryModel.class::isInstance)
                .map(ItemDisplayCategoryModel.class::cast)
                .findFirst().orElseThrow();
    }

    private static void assertSavedLore(ItemEditorModel model, String expected) {
        var context = model.getContext();
        assertEquals(expected, context.getItemStack().get(DataComponents.LORE).lines().getFirst().getString());
        ItemStack decoded = ItemStack.parseOptional(REGISTRY_ACCESS, context.getTag());
        assertFalse(decoded.isEmpty());
        assertEquals(expected, decoded.get(DataComponents.LORE).lines().getFirst().getString());
    }

    private static class TestItemEditorModel extends ItemEditorModel {
        TestItemEditorModel(ItemEditorContext context) {
            super(context);
        }

        @Override
        protected RegistryAccess.Frozen getRegistryAccess() {
            return REGISTRY_ACCESS;
        }
    }
}
