package com.bunnypranav.watchcalc.engine

/** A physical/chemical constant as it appears in the CONST browser. */
data class PhysConst(val symbol: String, val value: Double, val label: String)

/** CODATA values. Order is the order shown in the constants sheet. */
val CONST_LIST: List<PhysConst> = listOf(
    PhysConst("π", Math.PI, "pi"),
    PhysConst("e", Math.E, "Euler’s number"),
    PhysConst("g", 9.80665, "gravity  m/s²"),
    PhysConst("c", 299792458.0, "speed of light  m/s"),
    PhysConst("h", 6.62607015e-34, "Planck  J·s"),
    PhysConst("ħ", 1.054571817e-34, "reduced Planck  J·s"),
    PhysConst("G", 6.67430e-11, "grav. const  N·m²/kg²"),
    PhysConst("Na", 6.02214076e23, "Avogadro  /mol"),
    PhysConst("R", 8.314462618, "gas const  J/mol·K"),
    PhysConst("kB", 1.380649e-23, "Boltzmann  J/K"),
    PhysConst("qe", 1.602176634e-19, "elementary charge  C"),
    PhysConst("eV", 1.602176634e-19, "electronvolt  J"),
    PhysConst("me", 9.1093837015e-31, "electron mass  kg"),
    PhysConst("mp", 1.67262192369e-27, "proton mass  kg"),
    PhysConst("mn", 1.67492749804e-27, "neutron mass  kg"),
    PhysConst("u", 1.66053906660e-27, "atomic mass unit  kg"),
    PhysConst("ε0", 8.8541878128e-12, "permittivity  F/m"),
    PhysConst("μ0", 1.25663706212e-6, "permeability  H/m"),
    PhysConst("ke", 8.9875517873681764e9, "Coulomb const  N·m²/C²"),
    PhysConst("F", 96485.33212, "Faraday  C/mol"),
    PhysConst("σ", 5.670374419e-8, "Stefan–Boltzmann  W/m²K⁴"),
    PhysConst("bW", 2.897771955e-3, "Wien displacement  m·K"),
    PhysConst("Rinf", 1.0973731568160e7, "Rydberg  /m"),
    PhysConst("a0", 5.29177210903e-11, "Bohr radius  m"),
    PhysConst("atm", 101325.0, "atmosphere  Pa"),
    PhysConst("Vm", 0.02241396954, "molar volume STP  m³/mol"),
    PhysConst("Me", 5.9722e24, "Earth mass  kg"),
    PhysConst("Re", 6.371e6, "Earth radius  m"),
    PhysConst("ly", 9.4607304725808e15, "light year  m"),
    PhysConst("au", 1.495978707e11, "astronomical unit  m")
)

internal val CONSTS: Map<String, Double> = CONST_LIST.associate { it.symbol to it.value }
