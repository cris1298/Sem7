package com.example.sem7;

public class Usuario {
    private String userId;
    private String nombre;
    private String email;
    private String telefono;
    private String genero;
    private String direccion;

    // Constructor vacío requerido para Firebase
    public Usuario() {
        // Requerido para Firebase
    }

    public Usuario(String nombre, String telefono, String genero, String direccion, String email) {
        this.nombre = nombre;
        this.telefono = telefono;
        this.genero = genero;
        this.direccion = direccion;
        this.email = email;
    }

    // Getters y setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getGenero() {
        return genero;
    }

    public void setGenero(String genero) {
        this.genero = genero;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }
}