package com.github.rinorsi.cadeditor.client.screen.model.entry;

import com.github.rinorsi.cadeditor.client.screen.model.category.CategoryModel;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.Consumer;

import static com.github.franckyi.guapi.api.GuapiHelper.*;

public class TextEntryModel extends ValueEntryModel<MutableComponent> {
    private Runnable onApply;
    private boolean deletable = true;
    private boolean legacyColorCodes;
    private String legacyResetCode = "\u00a7r";

    public String getLegacyResetCode() {
        return legacyResetCode;
    }

    public void setLegacyResetCode(String legacyResetCode) {
        this.legacyResetCode = legacyResetCode;
    }

    public String normalizeLegacyResets(String value) {
        return value.replace("\u00a7r", legacyResetCode).replace("\u00a7R", legacyResetCode);
    }

    public boolean usesLegacyColorCodes() {
        return legacyColorCodes;
    }

    public void setLegacyColorCodes(boolean legacyColorCodes) {
        this.legacyColorCodes = legacyColorCodes;
    }

    public TextEntryModel(CategoryModel category, MutableComponent label, MutableComponent value, Consumer<MutableComponent> action) {
        super(category, label, value == null ? text() : value, action);
    }

    public Runnable getOnApply() {
        return onApply;
    }

    public void setOnApply(Runnable onApply) {
        this.onApply = onApply;
    }

    @Override
    public void apply() {
        // Legacy fields update immediately; a detached view must not overwrite them on save.
        if (!usesLegacyColorCodes() && onApply != null) {
            onApply.run();
        }
        super.apply();
    }

    @Override
    public Type getType() {
        return Type.TEXT;
    }

    public void resetDefaultValue() {
        defaultValue = getValue();
    }

    public void setDeletable(boolean deletable) {
        this.deletable = deletable;
    }

    @Override
    public boolean isDeletable() {
        return deletable && super.isDeletable();
    }
}
