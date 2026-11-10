package io.github.etzewetze.servernavigator.presentation;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;

/** Splits rich text without losing inherited colours, decorations, fonts or events. */
public final class ComponentLines {
    private ComponentLines() { }
    public static List<Component> split(Component component) {
        var lines = new ArrayList<Component>(); lines.add(Component.empty());
        append(component, Style.empty(), lines); return List.copyOf(lines);
    }
    private static void append(Component component, Style parent, List<Component> lines) {
        Style inherited = component.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text) {
            String[] parts = text.content().split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i != 0) lines.add(Component.empty());
                if (!parts[i].isEmpty()) add(lines, Component.text(parts[i]).style(inherited));
            }
        } else add(lines, component.children(List.of()).style(inherited));
        for (Component child : component.children()) append(child, inherited, lines);
    }
    private static void add(List<Component> lines, Component component) {
        int last = lines.size() - 1; lines.set(last, lines.get(last).append(component));
    }
    /** Bossbars/actionbars are single-line surfaces; retain words without newline glyphs. */
    public static Component singleLine(Component component) {
        Component result = Component.empty(); boolean first = true;
        for (Component line : split(component)) {
            if (!first) result = result.append(Component.space());
            result = result.append(line); first = false;
        }
        return result;
    }
}
