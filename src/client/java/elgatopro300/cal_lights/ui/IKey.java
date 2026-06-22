package elgatopro300.cal_lights.ui;

public interface IKey {
    String get();
    String get(Object... args);

    static IKey lang(String key) {
        return new IKey() {
            @Override
            public String get() {
                return L10n.get(key);
            }

            @Override
            public String get(Object... args) {
                try {
                    return String.format(L10n.get(key), args);
                } catch (Throwable t) {
                    return L10n.get(key);
                }
            }

            @Override
            public String toString() {
                return get();
            }
        };
    }
}
