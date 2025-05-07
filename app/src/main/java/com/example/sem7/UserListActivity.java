package com.example.sem7;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class UserListActivity extends AppCompatActivity {

    private static final String TAG = "UserListActivity";
    private ListView listViewUsers;
    private SearchView searchView;
    private Button btnPrevious;
    private Button btnNext;
    private TextView tvPageInfo;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private List<Usuario> userList;
    private List<Usuario> userListFull;
    private ArrayAdapter<Usuario> adapter;

    // Parámetros de paginación
    private static final int USERS_PER_PAGE = 10;
    private int currentPage = 1;
    private boolean isLastPage = false;
    private String lastVisibleKey = null;
    private List<String> pageKeys = new ArrayList<>();

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

        // Inicializar vistas
        listViewUsers = findViewById(R.id.listViewUsers);
        searchView = findViewById(R.id.searchView);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnNext = findViewById(R.id.btnNext);
        tvPageInfo = findViewById(R.id.tvPageInfo);

        // Inicializar listas
        userList = new ArrayList<>();
        userListFull = new ArrayList<>();
        pageKeys.add(null); // Página 1 comienza sin key

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
                if (newText == null || newText.isEmpty()) {
                    // Si la búsqueda se vacía, volvemos a la carga paginada normal
                    resetPagination();
                    loadUsers();
                } else {
                    // Realizar búsqueda en toda la base de datos
                    performSearch(newText);
                }
                return false;
            }
        });

        // Configurar botones de paginación
        btnPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPage > 1) {
                    currentPage--;
                    lastVisibleKey = pageKeys.get(currentPage - 1);
                    loadUsers();
                    updatePageInfo();
                }
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isLastPage) {
                    currentPage++;
                    // Si ya tenemos la key para esta página, úsala
                    if (pageKeys.size() >= currentPage) {
                        lastVisibleKey = pageKeys.get(currentPage - 1);
                    }
                    loadUsers();
                    updatePageInfo();
                }
            }
        });

        // Inicializar información de página
        updatePageInfo();
    }

    private void resetPagination() {
        currentPage = 1;
        isLastPage = false;
        lastVisibleKey = null;
        pageKeys.clear();
        pageKeys.add(null); // Página 1 comienza sin key
        updatePageInfo();
    }

    private void updatePageInfo() {
        tvPageInfo.setText("Página " + currentPage);
        btnPrevious.setEnabled(currentPage > 1);
        btnNext.setEnabled(!isLastPage);
    }

    private void setupAdapter() {
        // Código del adaptador
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
        Log.d(TAG, "Cargando usuarios para página " + currentPage);
        try {
            // Limpiar la lista actual
            userList.clear();
            adapter.notifyDataSetChanged();

            // Crear la consulta paginada
            Query query;
            if (lastVisibleKey == null) {
                // Primera página
                query = mDatabase.orderByKey().limitToFirst(USERS_PER_PAGE + 1);
            } else {
                // Páginas subsiguientes
                query = mDatabase.orderByKey().startAt(lastVisibleKey).limitToFirst(USERS_PER_PAGE + 1);
            }

            query.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    userList.clear();
                    userListFull.clear();

                    Log.d(TAG, "Recibidos: " + dataSnapshot.getChildrenCount() + " usuarios");

                    // Procesar los resultados
                    List<Usuario> pageUsers = new ArrayList<>();
                    String newLastKey = null;
                    int count = 0;

                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        count++;
                        // Si es el último elemento y tenemos más de USERS_PER_PAGE, no lo agregamos a la página actual
                        if (count > USERS_PER_PAGE) {
                            newLastKey = snapshot.getKey();
                            continue;
                        }

                        try {
                            Usuario user = snapshot.getValue(Usuario.class);
                            if (user != null) {
                                pageUsers.add(user);
                                if (count == USERS_PER_PAGE) {
                                    // Guardamos la key del último usuario visible para la siguiente página
                                    newLastKey = snapshot.getKey();
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error al procesar usuario", e);
                        }
                    }

                    // Actualizar la lista y el estado de paginación
                    userList.addAll(pageUsers);
                    userListFull.addAll(pageUsers);
                    adapter.notifyDataSetChanged();

                    // Determinar si es la última página
                    isLastPage = (dataSnapshot.getChildrenCount() <= USERS_PER_PAGE);

                    // Actualizar las keys de página
                    if (newLastKey != null) {
                        if (pageKeys.size() <= currentPage) {
                            pageKeys.add(newLastKey);
                        } else {
                            pageKeys.set(currentPage, newLastKey);
                        }
                        lastVisibleKey = newLastKey;
                    }

                    // Actualizar UI de paginación
                    updatePageInfo();

                    if (userList.isEmpty()) {
                        Toast.makeText(UserListActivity.this, "No hay más usuarios", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.d(TAG, "Cargados " + userList.size() + " usuarios en página " + currentPage);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Error al cargar usuarios: " + databaseError.getMessage(), databaseError.toException());
                    Toast.makeText(UserListActivity.this, "Error al cargar datos: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error al realizar consulta paginada", e);
            Toast.makeText(this, "Error al cargar datos", Toast.LENGTH_SHORT).show();
        }
    }

    private void performSearch(String query) {
        Log.d(TAG, "Realizando búsqueda: " + query);

        // Para búsquedas, cargamos todos los datos sin paginación
        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<Usuario> searchResults = new ArrayList<>();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        Usuario user = snapshot.getValue(Usuario.class);
                        if (user != null &&
                                ((user.getNombre() != null && user.getNombre().toLowerCase().contains(query.toLowerCase())) ||
                                        (user.getEmail() != null && user.getEmail().toLowerCase().contains(query.toLowerCase())))) {
                            searchResults.add(user);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error al procesar usuario en búsqueda", e);
                    }
                }

                // Actualizar la interfaz con los resultados
                userList.clear();
                userList.addAll(searchResults);
                userListFull.clear();
                userListFull.addAll(searchResults);
                adapter.notifyDataSetChanged();

                // Ocultar controles de paginación durante la búsqueda
                btnPrevious.setVisibility(View.GONE);
                btnNext.setVisibility(View.GONE);
                tvPageInfo.setText("Resultados de búsqueda: " + searchResults.size());

                if (searchResults.isEmpty()) {
                    Toast.makeText(UserListActivity.this, "No se encontraron resultados", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error al realizar búsqueda: " + databaseError.getMessage());
                Toast.makeText(UserListActivity.this, "Error al realizar búsqueda", Toast.LENGTH_SHORT).show();
            }
        });
    }
}