package com.github.rinorsi.cadeditor.client.screen.model.category.item;

import com.github.rinorsi.cadeditor.client.screen.model.ItemEditorModel;
import com.github.rinorsi.cadeditor.client.screen.model.entry.EntryModel;
import com.github.rinorsi.cadeditor.client.screen.model.entry.TextEntryModel;
import com.github.rinorsi.cadeditor.common.ModTexts;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public class ItemDisplayCategoryModel extends ItemEditorCategoryModel {
    private TextEntryModel itemNameEntry;
    private TextEntryModel customNameEntry;

    public ItemDisplayCategoryModel(ItemEditorModel editor) {
        super(ModTexts.DISPLAY, editor);
    }

    @Override
    protected void setupEntries() {
        itemNameEntry = new TextEntryModel(this, ModTexts.ITEM_NAME, getItemNameOverride(), this::setItemNameOverride);
        customNameEntry = new TextEntryModel(this, ModTexts.CUSTOM_NAME, getCustomName(), this::setCustomName);
        itemNameEntry.setLegacyColorCodes(true);
        customNameEntry.setLegacyColorCodes(true);
        getEntries().add(itemNameEntry);
        getEntries().add(customNameEntry);
        ItemLore lore = getStack().get(DataComponents.LORE);
        if (lore != null) {
            lore.lines().stream()
                    .map(Component::copy)
                    .map(this::createLoreEntry)
                    .forEach(getEntries()::add);
        }
    }

    @Override
    public int getEntryListStart() {
        return 2;
    }

    @Override
    public MutableComponent getAddListEntryButtonTooltip() {
        return ModTexts.LORE_ADD;
    }

    @Override
    public EntryModel createNewListEntry() {
        return createLoreEntry(null);
    }

    private EntryModel createLoreEntry(MutableComponent value) {
        TextEntryModel entry = new TextEntryModel(this, null, value, lore -> {});
        entry.setLegacyColorCodes(true);
        entry.setLegacyResetCode("\u00a77");
        entry.listIndexProperty().addListener(index -> entry.setLabel(ModTexts.lore(index + 1)));
        entry.valueProperty().addListener(ignored -> syncLoreToStack());
        return entry;
    }

    @Override
    public void apply() {
        super.apply();
        setItemNameOverride(itemNameEntry.getValue());
        setCustomName(customNameEntry.getValue());
        syncLoreToStack();
        ItemStack stack = getStack();
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null && customName.getString().isEmpty()) {
            stack.remove(DataComponents.CUSTOM_NAME);
        }
    }

    private void syncLoreToStack() {
        ItemStack stack = getStack();
        List<Component> loreLines = getLoreEntries().stream()
                .map(TextEntryModel::getValue)
                .map(ItemDisplayCategoryModel::normalizeLoreLine)
                .filter(v -> v != null)
                .map(Component.class::cast)
                .toList();
        if (!loreLines.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(loreLines));
        } else if (stack.get(DataComponents.LORE) instanceof ItemLore existingLore
                && !existingLore.lines().isEmpty()) {
            stack.remove(DataComponents.LORE);
        }
    }

    private MutableComponent getItemNameOverride() {
        Component component = getStack().get(DataComponents.ITEM_NAME);
        if (component != null) {
            return component.copy();
        }
        return Component.translatable(getStack().getDescriptionId()).copy();
    }

    private void setItemNameOverride(MutableComponent value) {
        ItemStack stack = getStack();
        Component defaultComponent = new ItemStack(stack.getItem()).get(DataComponents.ITEM_NAME);
        Component defaultName = defaultComponent != null ? defaultComponent
                : Component.translatable(stack.getDescriptionId());
        if (isBlank(value) || value.getString().equals(defaultName.getString())) {
            if (defaultComponent != null) {
                stack.set(DataComponents.ITEM_NAME, defaultComponent);
            } else if (stack.has(DataComponents.ITEM_NAME)) {
                stack.remove(DataComponents.ITEM_NAME);
            }
            return;
        }
        stack.set(DataComponents.ITEM_NAME, plainName(value));
    }

    private MutableComponent getCustomName() {
        Component component = getStack().get(DataComponents.CUSTOM_NAME);
        return component == null ? null : component.copy();
    }

    private void setCustomName(MutableComponent value) {
        if (isBlank(value)) {
            getStack().remove(DataComponents.CUSTOM_NAME);
            return;
        }
        getStack().set(DataComponents.CUSTOM_NAME, plainName(value));
    }

    private List<TextEntryModel> getLoreEntries() {
        int start = getEntryListStart();
        if (start < 0) {
            return List.of();
        }
        int end = getEntries().size();
        if (canAddEntryInList()) {
            end -= 1;
        }
        if (start >= end) {
            return List.of();
        }
        return getEntries().subList(start, end).stream()
                .filter(TextEntryModel.class::isInstance)
                .map(TextEntryModel.class::cast)
                .toList();
    }

    static MutableComponent plainName(Component value) {
        return Component.literal(value.getString());
    }

    static MutableComponent normalizeLoreLine(Component value) {
        if (isBlank(value)) {
            return null;
        }
        String text = value.getString().replace("\u00a7r", "\u00a77").replace("\u00a7R", "\u00a77");
        return Component.literal(text.startsWith("\u00a77") ? text : "\u00a77" + text);
    }

    private static boolean isBlank(Component c) {
        return c == null || c.getString().isEmpty();
    }

    private ItemStack getStack() {
        return getParent().getContext().getItemStack();
    }
}
