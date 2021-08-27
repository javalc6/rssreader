package livio.rssreader.backend;

/** Holds item data. */
public final class Item {
    String name = "";
    private boolean checked;

    public Item(String name, boolean checked) {
        this.name = name;
        this.checked = checked;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public void toggleChecked() {
        checked = !checked;
    }
}
