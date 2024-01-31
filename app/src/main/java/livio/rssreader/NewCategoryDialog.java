package livio.rssreader;
/*
Version 1.0, 16-08-2021, First release by Livio (javalc6@gmail.com)

IMPORTANT NOTICE, please read:

This software is licensed under the terms of the GNU GENERAL PUBLIC LICENSE,
please read the enclosed file license.txt or https://www.gnu.org/licenses/old-licenses/gpl-2.0-standalone.html

Note that this software is freeware and it is not designed, licensed or intended
for use in mission critical, life support and military purposes.

The use of this software is at the risk of the user.
*/
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatDialogFragment;
//called by ListFeeds

public final class NewCategoryDialog extends AppCompatDialogFragment implements OnClickListener {

	private EditText mCatDescription, mCatTitle;
	private Button okButton;
	private Button cancelButton;

    private NewCategoryDialog() {//new feed
        // Empty constructor required for DialogFragment
    }

    static NewCategoryDialog newInstance(String[] category) {
        NewCategoryDialog f = new NewCategoryDialog();

        Bundle args = new Bundle();
        args.putStringArray("category", category);
        f.setArguments(args);
        return f;
    }


    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	         Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.new_category_dialog, container);
	    mCatTitle = view.findViewById(R.id.new_category_title);
        mCatDescription = view.findViewById(R.id.new_category_description);
        String[] category = getArguments().getStringArray("category");
        if (category != null) {
            if (category[0] != null) //title
                mCatTitle.setText(category[0]);
            if (category[1] != null) //url
                mCatDescription.setText(category[1]);
            getDialog().setTitle((category[0] == null) ? getString(R.string.dlg_add_category) : getString(R.string.dlg_edit_category));//check if add new category o edit category
        }
	    okButton = view.findViewById(R.id.ok_btn);
	    cancelButton = view.findViewById(R.id.cancel_btn);

	    okButton.setOnClickListener(this);
	    cancelButton.setOnClickListener(this);
	    return view;
	        
    }
	 
    public void onClick(View target) {
		if (target == cancelButton)
		    this.dismiss();
		else if (target == okButton) {//add new category or edit an existing category
			String[] category = getArguments().getStringArray("category");
			if (mCatTitle.getText().toString().length() > 0) {
				category[0] = mCatTitle.getText().toString();//title
				category[1] = mCatDescription.getText().toString();//url
				Bundle result = new Bundle();
				result.putStringArray("category", category);
				getParentFragmentManager().setFragmentResult("category_key", result);
				this.dismiss();
			} else {
				mCatTitle.setError(getString(R.string.missing_title));
//					Toast.makeText(act, R.string.missing_title, Toast.LENGTH_SHORT).show();
			}
        }
    }

}