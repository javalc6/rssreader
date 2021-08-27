package livio.rssreader.backend;

public final class IconItem {
	public final String name;
	final String resourceId;
    private boolean checked;

	public IconItem(String name, String resourceFilePath, boolean checked) {
		this.name = name;
		this.resourceId = resourceFilePath;
        this.checked = checked;
	}

	@Override
	public String toString() {
		return this.name;
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
