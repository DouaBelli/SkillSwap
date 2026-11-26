package com.skillswap.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "department")
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "department_id")
    private Integer id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    public Integer getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}