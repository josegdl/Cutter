/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intelite.models;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 *
 * @author Desarrollo
 */
@Entity
@Table(name = "segmentador.corte")
public class Corte implements Serializable {

    @Id
    private Integer capclave;
    private String src;
    private Date fecha;
    private String time_ini;
    private String time_fin;
    private String duracion;
    private Integer status;
    private String nombre_archivo;
    private Integer tipo_origen;
    private Integer intentos;
    private Integer origen;

    public Corte() {
    }

    public Integer getCapclave() {
        return capclave;
    }

    public void setCapclave(Integer capclave) {
        this.capclave = capclave;
    }

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    public Date getFecha() {
        return fecha;
    }

    public void setFecha(Date fecha) {
        this.fecha = fecha;
    }

    public String getTime_ini() {
        return time_ini;
    }

    public void setTime_ini(String time_ini) {
        this.time_ini = time_ini;
    }

    public String getTime_fin() {
        return time_fin;
    }

    public void setTime_fin(String time_fin) {
        this.time_fin = time_fin;
    }

    public String getDuracion() {
        return duracion;
    }

    public void setDuracion(String duracion) {
        this.duracion = duracion;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getNombre_archivo() {
        return nombre_archivo;
    }

    public void setNombre_archivo(String nombre_archivo) {
        this.nombre_archivo = nombre_archivo;
    }

    public Integer getTipo_origen() {
        return tipo_origen;
    }

    public void setTipo_origen(Integer tipo_origen) {
        this.tipo_origen = tipo_origen;
    }

    public Integer getIntentos() {
        return intentos;
    }

    public void setIntentos(Integer intentos) {
        this.intentos = intentos;
    }

    public Integer getOrigen() {
        return origen;
    }

    public void setOrigen(Integer origen) {
        this.origen = origen;
    }

    @Override
    public String toString() {
        return "Corte{" + "capclave=" + capclave + ", src=" + src + ", fecha=" + fecha + ", time_ini=" + time_ini + ", time_fin=" + time_fin + ", duracion=" + duracion + ", status=" + status + ", nombre_archivo=" + nombre_archivo + ", tipo_origen=" + tipo_origen + ", intentos=" + intentos + ", origen=" + origen + '}';
    }

}
