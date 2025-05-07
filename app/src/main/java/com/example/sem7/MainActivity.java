package com.example.sem7;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.BuildConfig;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 9001;

    private EditText etNombre, etTelefono, etDireccion, etEmail, etPassword;
    private RadioGroup rgGenero;
    private Button btnGuardar, btnVerUsuarios;
    private com.google.android.gms.common.SignInButton btnGoogle;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Configuración única de Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Configuración para desarrollo (emulador)
        if (BuildConfig.DEBUG) {
            mAuth.useEmulator("10.0.2.2", 9099);
            mAuth.getFirebaseAuthSettings().setAppVerificationDisabledForTesting(true);
        }

        // Configurar Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Inicializar Firebase Auth y Database
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Vincular vistas
        etNombre = findViewById(R.id.etNombre);
        etTelefono = findViewById(R.id.etTelefono);
        rgGenero = findViewById(R.id.rgGenero);
        etDireccion = findViewById(R.id.etDireccion);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnGuardar = findViewById(R.id.btnGuardar);
        btnGoogle = findViewById(R.id.btnGoogle);
        btnVerUsuarios = findViewById(R.id.btnVerUsuarios);

        btnGuardar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registrarUsuario();
            }
        });

        btnGoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signInWithGoogle();
            }
        });

        btnVerUsuarios.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, UserListActivity.class));
            }
        });
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Error en autenticación con Google: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            guardarUsuarioGoogle(user);
                        } else {
                            Toast.makeText(MainActivity.this, "Error en autenticación con Firebase", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void guardarUsuarioGoogle(FirebaseUser user) {
        if (user != null) {
            Usuario usuario = new Usuario(
                    user.getDisplayName() != null ? user.getDisplayName() : "Sin nombre",
                    user.getPhoneNumber() != null ? user.getPhoneNumber() : "No especificado",
                    "No especificado",
                    "No especificada",
                    user.getEmail() != null ? user.getEmail() : ""
            );
            usuario.setUserId(user.getUid());

            mDatabase.child("usuarios").child(user.getUid())
                    .setValue(usuario)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Verificación de guardado
                            mDatabase.child("usuarios").child(user.getUid())
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                                            Log.d("FIREBASE", "Datos guardados: " + snapshot.getValue());
                                        }
                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            Log.e("FIREBASE", "Error al verificar: " + error.getMessage());
                                        }
                                    });

                            startActivity(new Intent(MainActivity.this, UserListActivity.class));
                            finish();
                        }
                    });
        }
    }

    private void registrarUsuario() {
        final String nombre = etNombre.getText().toString().trim();
        final String telefono = etTelefono.getText().toString().trim();

        int selectedId = rgGenero.getCheckedRadioButtonId();
        if (selectedId == -1) {
            Toast.makeText(this, "Seleccione un género", Toast.LENGTH_SHORT).show();
            return;
        }
        RadioButton radioButton = findViewById(selectedId);
        final String genero = radioButton.getText().toString();

        final String direccion = etDireccion.getText().toString().trim();
        final String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(nombre) || TextUtils.isEmpty(telefono) ||
                TextUtils.isEmpty(direccion) || TextUtils.isEmpty(email) ||
                TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Complete todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            // Crear objeto Usuario
                            Usuario usuario = new Usuario(
                                    nombre,
                                    telefono,
                                    genero,
                                    direccion,
                                    email
                            );

                            // Guardar bajo el nodo "usuarios" con el UID como clave
                            mDatabase.child("usuarios").child(firebaseUser.getUid())
                                    .setValue(usuario)
                                    .addOnCompleteListener(dbTask -> {
                                        if (dbTask.isSuccessful()) {
                                            Log.d("FIREBASE", "Usuario guardado correctamente");
                                            // Verificar en la consola
                                            mDatabase.child("usuarios").child(firebaseUser.getUid())
                                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                                        @Override
                                                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                            Log.d("FIREBASE", "Datos guardados: " + snapshot.getValue());
                                                        }
                                                        @Override
                                                        public void onCancelled(@NonNull DatabaseError error) {
                                                            Log.e("FIREBASE", "Error al verificar: " + error.getMessage());
                                                        }
                                                    });
                                        } else {
                                            Log.e("FIREBASE", "Error al guardar: " + dbTask.getException());
                                        }
                                    });
                        }
                    }
                });
    }
}