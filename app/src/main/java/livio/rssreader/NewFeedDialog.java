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
import android.app.Activity;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
//called by ListFeeds

public final class NewFeedDialog extends AppCompatDialogFragment implements OnClickListener {

	private EditText mRssUrl, mRSSTitle;
	private Button okButton;
	private Button cancelButton;

    private NewFeedDialog() {//new feed
        // Empty constructor required for DialogFragment
    }

    public int getTheme() {//Workaround for issue 37059987
        return R.style.FixedDialog;
    }

    static NewFeedDialog newInstance(String[] feed) {
        NewFeedDialog f = new NewFeedDialog();

        Bundle args = new Bundle();
        args.putStringArray("feed", feed);
        f.setArguments(args);
        return f;
    }


    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	         Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.new_feed_dialog, container);
	    mRSSTitle = view.findViewById(R.id.new_rssfeed_title);
        mRssUrl = view.findViewById(R.id.new_rssfeed_url);
        String[] feed = getArguments().getStringArray("feed");
        if (feed != null) {
            if (feed[0] != null) //title
                mRSSTitle.setText(feed[0]);
            if (feed[1] != null) //url
                mRssUrl.setText(feed[1]);
            getDialog().setTitle((feed[2] == null) ? getString(R.string.dlg_add_feed) : getString(R.string.dlg_edit_feed));//check if add new feed o edit feed
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
		else if (target == okButton) {//add new feed or edit an existing feed
			Activity act = getActivity();
			if (act != null) {
                String[] feed = getArguments().getStringArray("feed");
				if (mRSSTitle.getText().toString().length() > 0) {
					if (mRssUrl.getText().toString().length() > 0) {
                        feed[0] = mRSSTitle.getText().toString();//title
                        feed[1] = mRssUrl.getText().toString();//url
                        EditNameDialogListener listener = getTargetFragment() == null ? (EditNameDialogListener) act :
                                (EditNameDialogListener) getTargetFragment();//return results back to caller
                        listener.onFinishEditDialog(feed);
						 this.dismiss();
					} else {
						mRssUrl.setError(getString(R.string.missing_url));
					}
				} else {
					mRSSTitle.setError(getString(R.string.missing_title));
				}
            } else this.dismiss();
        }
    }

    public interface EditNameDialogListener {//interface to pass results back
        void onFinishEditDialog(String[] feed);
    }
 
}