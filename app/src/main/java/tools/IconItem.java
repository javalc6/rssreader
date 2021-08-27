package tools;

import android.content.Intent;

public final class IconItem {
    public final Intent intent;
    public final String name;
	public final int resourceId;
    public final String packagename;

	public IconItem(Intent intent, String name, int resourceId, String packagename) {
        this.intent = intent;
        this.name = name;
		this.resourceId = resourceId;
        this.packagename = packagename;
	}

	@Override
	public String toString() {
		return this.name;
	}
}
