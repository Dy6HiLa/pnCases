package ru.privatenull.hologram;

public interface HologramProvider {

    boolean isAvailable();

    void create(HologramSpec spec);

    void remove(String name);
}
