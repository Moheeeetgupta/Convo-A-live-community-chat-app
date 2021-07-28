package com.mohit.letschat;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mMessagesDatabaseReference;
    private ChildEventListener mChildEventListener;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    // Choose an arbitrary request code value
    private static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER =  2;

    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotoStorageReference;
    private FirebaseRemoteConfig mfirebaseRemoteConfig;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        // Initialize Firebase components
        mFirebaseDatabase=FirebaseDatabase.getInstance ();
        mFirebaseAuth=FirebaseAuth.getInstance ();
        mFirebaseStorage =FirebaseStorage.getInstance ();
        mMessagesDatabaseReference=mFirebaseDatabase.getReference ().child ("messages");
        mChatPhotoStorageReference=mFirebaseStorage.getReference ().child ("chat_photos");



        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);



    }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                mMessagesDatabaseReference.push ().setValue (friendlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });


        mAuthStateListener=new FirebaseAuth.AuthStateListener () {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
            FirebaseUser user=firebaseAuth.getCurrentUser ();
            if(user!=null){
                //user is signed in
                onSignedInInitialize(user.getDisplayName ());
            }else{
               //user is signed out
                onSignOutCleanUp();
                startActivityForResult(
                        AuthUI.getInstance()
                                .createSignInIntentBuilder()
                                .setIsSmartLockEnabled (false)
                                .setAvailableProviders(Arrays.asList(
                                        new AuthUI.IdpConfig.GoogleBuilder().build(),

                                        new AuthUI.IdpConfig.EmailBuilder().build()))
                                .build(),
                        RC_SIGN_IN);
            }
            }
        };
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult (requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {

            if (resultCode == RESULT_OK) {
                Toast.makeText (this, "Signed in!", Toast.LENGTH_SHORT).show ();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText (this, "Sign in cancelled", Toast.LENGTH_SHORT).show ();
                finish ();
            }
        }else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
                Uri selectedImageUri = data.getData ();

                StorageReference photoRef = mChatPhotoStorageReference.child (selectedImageUri.getLastPathSegment ());
                UploadTask uploadTask = photoRef.putFile (selectedImageUri);
                // Register observers to listen for when the download is done or if it fails
                uploadTask.continueWithTask (new Continuation<UploadTask.TaskSnapshot, Task<Uri>> () {
                    @Override
                    public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                        if (!task.isSuccessful ()) {
                            throw task.getException ();
                        }

                        // Continue with the task to get the download URL
                        return photoRef.getDownloadUrl ();
                    }
                }).addOnCompleteListener (new OnCompleteListener<Uri> () {
                    @Override
                    public void onComplete(@NonNull Task<Uri> task) {
                        if (task.isSuccessful ()) {
                            Uri downloadUrl = task.getResult ();
                            FriendlyMessage friendlyMessage = new FriendlyMessage ( null, mUsername,downloadUrl.toString ());
                            mMessagesDatabaseReference.push ().setValue (friendlyMessage);

                        } else {

                            // Handle failures
                            // ...
                        }
                    }
                });

            }
        }

    private void onSignOutCleanUp() {
        mUsername=ANONYMOUS;
        mMessageAdapter.clear ();
        detachDatabaseReadListeners();
    }

    private void detachDatabaseReadListeners() {
        if(mChildEventListener!=null){
        mMessagesDatabaseReference.removeEventListener (mChildEventListener);
        mChildEventListener=null;
        }
    }

    private void onSignedInInitialize(String displayName) {
        mUsername=displayName;
        attachDatabaseReadListeners();

    }

    private void attachDatabaseReadListeners() {
        if(mChildEventListener==null){
        mChildEventListener =new ChildEventListener () {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                FriendlyMessage friendlyMessage= snapshot.getValue (FriendlyMessage.class);
                mMessageAdapter.add (friendlyMessage);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        };

        mMessagesDatabaseReference.addChildEventListener (mChildEventListener);
    }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId ()){
            case  R.id.sign_out_menu:
                AuthUI.getInstance ().signOut (this);
                    return true;
            default:
               return super.onOptionsItemSelected (item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume ();
        mFirebaseAuth.addAuthStateListener (mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause ();
        if(mAuthStateListener!=null) {
            mFirebaseAuth.removeAuthStateListener (mAuthStateListener);
        }
        mMessageAdapter.clear ();
        detachDatabaseReadListeners();

    }
}
