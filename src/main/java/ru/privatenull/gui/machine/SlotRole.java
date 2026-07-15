package ru.privatenull.gui.machine;

enum SlotRole {
    EMPTY("Пусто", "&8"),
    DECOR("Декор", "&7"),
    HISTORY("История", "&e"),
    OPEN("Открытие кейса", "&a"),
    PREVIEW("Предпросмотр наград", "&d"),
    ANIMATION("Кнопка анимации", "&b");

    final String displayName;
    final String color;

    SlotRole(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    SlotRole next() {
        return switch (this) {
            case EMPTY -> DECOR;
            case DECOR -> HISTORY;
            case HISTORY -> OPEN;
            case OPEN -> PREVIEW;
            case PREVIEW -> ANIMATION;
            case ANIMATION -> EMPTY;
        };
    }

    SlotRole previous() {
        return switch (this) {
            case EMPTY -> ANIMATION;
            case ANIMATION -> PREVIEW;
            case PREVIEW -> OPEN;
            case OPEN -> HISTORY;
            case HISTORY -> DECOR;
            case DECOR -> EMPTY;
        };
    }
}
