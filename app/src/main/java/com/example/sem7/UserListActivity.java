package com.example.sem7;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.BuildConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class UserListActivity extends AppCompatActivity {

    private static final String TAG = "UserListActivity";
    private ListView listViewUsers;
    private SearchView searchView;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private List<Usuario> userList;
    private List<Usuario> userListFull;
    private ArrayAdapter<Usuario> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_list);

        // Inicializar Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Configuración para desarrollo (emulador) - IMPORTANTE
        if (BuildConfig.DEBUG) {
            try {
                mAuth.useEmulator("10.0.2.2", 9099);

                // También configurar el emulador para la base de datos
                FirebaseDatabase.getInstance().useEmulator("10.0.2.2", 9000);

                Log.d(TAG, "Usando emulador de Firebase");
            } catch (Exception e) {
                Log.e(TAG, "Error al configurar emulador", e);
            }
        }

        listViewUsers = findViewById(R.id.listViewUsers);
        searchView = findViewById(R.id.searchView);

        // Inicializar listas
        userList = new ArrayList<>();
        userListFull = new ArrayList<>();

        // Configurar el adaptador
        setupAdapter();

        // Verificar autenticación antes de cargar datos
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Usuario ya está autenticado
            initializeFirebaseAndLoadData();
        } else {
            // Redirigir a MainActivity
            Toast.makeText(this, "Debe iniciar sesión primero", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(UserListActivity.this, MainActivity.class));
            finish();
        }

        // Configurar búsqueda
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return false;
            }
        });
    }

    private void setupAdapter() {
        // Código del adaptador (sin cambios)
        adapter = new ArrayAdapter<Usuario>(this, R.layout.item_user, R.id.tvNombre, userList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);

                Usuario user = getItem(position);
                if(user != null) {
                    TextView tvNombre = view.findViewById(R.id.tvNombre);
                    TextView tvEmail = view.findViewById(R.id.tvEmail);
                    TextView tvTelefono = view.findViewById(R.id.tvTelefono);
                    TextView tvGenero = view.findViewById(R.id.tvGenero);
                    TextView tvDireccion = view.findViewById(R.id.tvDireccion);

                    tvNombre.setText(user.getNombre() != null ? user.getNombre() : "Sin nombre");
                    tvEmail.setText("Email: " + (user.getEmail() != null ? user.getEmail() : "No disponible"));
                    tvTelefono.setText("Teléfono: " + (user.getTelefono() != null ? user.getTelefono() : "No disponible"));
                    tvGenero.setText("Género: " + (user.getGenero() != null ? user.getGenero() : "No especificado"));
                    tvDireccion.setText("Dirección: " + (user.getDireccion() != null ? user.getDireccion() : "No disponible"));
                }
                return view;
            }

            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        List<Usuario> filteredList = new ArrayList<>();

                        if (constraint == null || constraint.length() == 0) {
                            filteredList.addAll(userListFull);
                        } else {
                            String filterPattern = constraint.toString().toLowerCase().trim();

                            for (Usuario user : userListFull) {
                                if ((user.getNombre() != null && user.getNombre().toLowerCase().contains(filterPattern)) ||
                                        (user.getEmail() != null && user.getEmail().toLowerCase().contains(filterPattern))) {
                                    filteredList.add(user);
                                }
                            }
                        }

                        results.values = filteredList;
                        results.count = filteredList.size();
                        return results;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        userList.clear();
                        userList.addAll((List<Usuario>) results.values);
                        notifyDataSetChanged();
                    }
                };
            }
        };

        listViewUsers.setAdapter(adapter);
    }

    private void initializeFirebaseAndLoadData() {
        try {
            mDatabase = FirebaseDatabase.getInstance().getReference("usuarios");
            Log.d(TAG, "Firebase reference initialized: " + mDatabase.toString());
            loadUsers();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase reference", e);
            Toast.makeText(this, "Error al conectar con Firebase", Toast.LENGTH_LONG).show();
        }
    }

    private void loadUsers() {
        Log.d(TAG, "Iniciando carga de usuarios...");
        try {
            mDatabase.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    userList.clear();
                    userListFull.clear();
                    Log.d(TAG, "Número de usuarios: " + dataSnapshot.getChildrenCount());

                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        try {
                            Log.d(TAG, "Procesando usuario key: " + snapshot.getKey());
                            Log.d(TAG, "Contenido del snapshot: " + snapshot.getValue().toString());

                            Usuario user = snapshot.getValue(Usuario.class);
                            if (user != null) {
                                userList.add(user);
                                userListFull.add(user);
                                Log.d(TAG, "Usuario cargado: " + user.getNombre());
                            } else {
                                Log.e(TAG, "Usuario es null después de convertir el snapshot");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error al procesar el usuario", e);
                        }
                    }

                    adapter.notifyDataSetChanged();

                    if(userList.isEmpty()) {
                        Log.e(TAG, "La lista de usuarios está vacía después de cargar datos");
                        Toast.makeText(UserListActivity.this, "No se encontraron usuarios", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.d(TAG, "Total de usuarios cargados: " + userList.size());
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Error al cargar usuarios: " + databaseError.getMessage(), databaseError.toException());
                    Toast.makeText(UserListActivity.this, "Error al cargar datos: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error al agregar valueEventListener", e);
            Toast.makeText(this, "Error al configurar la escucha de datos", Toast.LENGTH_LONG).show();
        }
    }
}