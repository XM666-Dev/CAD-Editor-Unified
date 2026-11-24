package com.github.rinorsi.cadeditor.client.util.texteditor;

import net.minecraft.ChatFormatting;

import java.util.LinkedHashSet;
import java.util.Set;

public final class LegacyColorCodes {
    private LegacyColorCodes() {
    }

    public static String prefix(String color) {
        ChatFormatting formatting = ChatFormatting.getByName(color);
        if (formatting == null || !formatting.isColor()) {
            throw new IllegalArgumentException("Not a legacy color: " + color);
        }
        return "\u00a7" + formatting.getChar();
    }

    public static String wrap(String text, int start, int end, String color) {
        return recolor(text, start, end, color).text();
    }

    public static String compact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length());
        FormatState active = new FormatState();
        FormatState pending = active.copy();
        for (int i = 0; i < text.length();) {
            ChatFormatting code = codeAt(text, i);
            if (code != null) {
                pending.accept(code);
                i += 2;
                continue;
            }
            appendTransition(result, active, pending);
            active = pending.copy();
            result.append(text.charAt(i++));
        }
        return result.toString();
    }

    private static void appendTransition(StringBuilder result, FormatState active, FormatState target) {
        if (active.equals(target)) {
            return;
        }
        if (active.color != target.color) {
            result.append(target.color == null ? ChatFormatting.RESET : target.color);
            target.styles.forEach(result::append);
            return;
        }
        if (target.styles.containsAll(active.styles)) {
            target.styles.stream().filter(style -> !active.styles.contains(style)).forEach(result::append);
            return;
        }
        result.append(ChatFormatting.RESET);
        if (target.color != null) {
            result.append(target.color);
        }
        target.styles.forEach(result::append);
    }

    public static Edit recolor(String text, int anchor, int cursor, String color) {
        String prefix = prefix(color);
        int start = Math.min(anchor, cursor);
        int end = Math.max(anchor, cursor);
        if (start < 0 || end > text.length()) {
            throw new IndexOutOfBoundsException();
        }
        int[] positions = new int[text.length() + 1];
        if (start == end) {
            for (int i = 0; i <= text.length(); i++) positions[i] = i;
            return new Edit(text, start, end, positions);
        }
        // Never leave half of a recognized formatting code outside the selection.
        if (start > 0 && codeAt(text, start - 1) != null) start--;
        if (end > 0 && codeAt(text, end - 1) != null) end++;

        StringBuilder result = new StringBuilder();
        State state = new State();
        int selectionStart = 0;
        int selectionEnd = 0;
        for (int i = 0; i <= text.length();) {
            if (i == start) {
                result.append(prefix);
                // A color code clears legacy styles, so reapply the styles at the boundary.
                state.appendStyles(result);
                selectionStart = result.length();
            }
            if (i == end) {
                selectionEnd = result.length();
                result.append("\u00a7r");
                if (end < text.length()) state.append(result);
            }
            positions[i] = result.length();
            if (i == text.length()) break;
            ChatFormatting code = codeAt(text, i);
            if (code != null) {
                boolean selected = i >= start && i < end;
                if (selected && (code.isColor() || code == ChatFormatting.RESET)) {
                    // Retain the style-reset effect without letting the old color win.
                    if (!state.styles.isEmpty()) result.append("\u00a7r").append(prefix);
                } else {
                    result.append(text, i, i + 2);
                }
                state.accept(code);
                positions[i + 1] = positions[i];
                i += 2;
            } else {
                result.append(text.charAt(i++));
            }
        }
        return new Edit(result.toString(), selectionStart, selectionEnd, positions);
    }

    private static ChatFormatting codeAt(String text, int index) {
        if (index >= 0 && index + 1 < text.length() && text.charAt(index) == '\u00a7') {
            return ChatFormatting.getByCode(text.charAt(index + 1));
        }
        return null;
    }

    public record Edit(String text, int selectionStart, int selectionEnd, int[] positions) {
        public int mapPosition(int position) {
            return positions[position];
        }
    }

    private static final class State {
        private ChatFormatting color;
        private final Set<ChatFormatting> styles = new LinkedHashSet<>();

        void accept(ChatFormatting code) {
            if (code.isColor() || code == ChatFormatting.RESET) {
                color = code.isColor() ? code : null;
                styles.clear();
            } else {
                styles.add(code);
            }
        }

        void append(StringBuilder result) {
            if (color != null) result.append(color);
            appendStyles(result);
        }

        void appendStyles(StringBuilder result) {
            styles.forEach(result::append);
        }
    }

    private static final class FormatState {
        private ChatFormatting color;
        private final Set<ChatFormatting> styles = new LinkedHashSet<>();

        void accept(ChatFormatting code) {
            if (code.isColor() || code == ChatFormatting.RESET) {
                color = code.isColor() ? code : null;
                styles.clear();
            } else {
                styles.add(code);
            }
        }

        FormatState copy() {
            FormatState copy = new FormatState();
            copy.color = color;
            copy.styles.addAll(styles);
            return copy;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof FormatState other && color == other.color && styles.equals(other.styles);
        }

        @Override
        public int hashCode() {
            return 31 * (color == null ? 0 : color.hashCode()) + styles.hashCode();
        }
    }
}
