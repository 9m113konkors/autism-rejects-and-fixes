package com.example.minimal.api;

import autismclient.api.module.Kind;
import autismclient.api.module.Setting;

// An integer setting that stores a (min, max) range in one value, so a single slider bar can carry
// two draggable handles. The raw stored value is packed as `min * 100 + max` (kept sorted, so the
// smaller end always lands in the hundreds). The host UI does not know how to render a range, so
// the addon's RangeSliderMixin draws the two-handle bar for any setting of this type.
public final class RangeSetting extends Setting<Integer, RangeSetting> {

    private final int domainMin;
    private final int domainMax;
    private final int step;

    public RangeSetting(String name, String title, int minValue, int maxValue,
                        int domainMin, int domainMax, int step) {
        super(Kind.INTEGER, name, title,
            pack(clamp(minValue, domainMin, Math.max(domainMin, domainMax)),
                clamp(maxValue, domainMin, Math.max(domainMin, domainMax))));
        this.domainMin = domainMin;
        this.domainMax = Math.max(domainMin, domainMax);
        this.step = Math.max(1, step);
        setRange(domainMin, this.domainMax);
        setSliderRange(domainMin, this.domainMax);
        setStep(this.step);
        formatter(raw -> {
            int[] pair = decodeRange(raw);
            return pair[0] + "-" + pair[1];
        });
    }

    public int minValue() {
        int[] pair = decodeRange(serialize());
        return pair[0];
    }

    public int maxValue() {
        int[] pair = decodeRange(serialize());
        return pair[1];
    }

    public int domainMin() {
        return domainMin;
    }

    public int domainMax() {
        return domainMax;
    }

    // Packs a range so it can be written through the host Module API (e.g. QuickConfig presets).
    public static String encode(int min, int max) {
        return Integer.toString(pack(clamp(min, 0, 999), clamp(max, 0, 999)));
    }

    public static int[] decodeRange(String raw) {
        int value = parse(raw);
        int min = value / 100;
        int max = value % 100;
        return new int[] { Math.min(min, max), Math.max(min, max) };
    }

    @Override
    protected Integer decode(String raw) {
        int[] pair = decodeRange(raw);
        return pack(clamp(pair[0], domainMin, domainMax), clamp(pair[1], domainMin, domainMax));
    }

    @Override
    protected String encode(Integer value) {
        int v = value == null ? defaultValueTyped() : value;
        int[] pair = unpack(v);
        return Integer.toString(pack(clamp(pair[0], domainMin, domainMax),
            clamp(pair[1], domainMin, domainMax)));
    }

    @Override
    protected Integer sanitizeTyped(Integer value) {
        int v = value == null ? defaultValueTyped() : value;
        int[] pair = unpack(v);
        return pack(clamp(pair[0], domainMin, domainMax), clamp(pair[1], domainMin, domainMax));
    }

    private static int pack(int min, int max) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        return lo * 100 + hi;
    }

    private static int[] unpack(int value) {
        int min = value / 100;
        int max = value % 100;
        return new int[] { Math.min(min, max), Math.max(min, max) };
    }

    private static int parse(String raw) {
        if (raw == null) return 0;
        try {
            return (int) Math.round(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
