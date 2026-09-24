# Zian Utilities

**Zian Utilities** es un mod modular para **Minecraft 1.21.1**, orientado principalmente a servidores con **Cobblemon 1.8+** sobre **NeoForge 1.21.1**, con compatibilidad final prevista para **Youer 1.21.1**.

> Estado actual: **Milestone 1 en desarrollo**. El bootstrap productivo ya está separado en `core` (JVM puro) y `neoforge`; el repositorio nace desde cero y no reutiliza la implementación del antiguo sistema de control de generaciones.

## Objetivo

Construir una base modular que permita incorporar utilidades para un servidor Cobblemon sin acoplar todos los sistemas entre sí.

Módulos previstos:

- **Core**
- **Cobblemon Integration**
- **Generation Control**
- **Quests**
  - Campaign
  - Daily
  - Weekly
- **Rewards**
- **Economy Integration**
- **Gacha**
- **Equipment**
  - Weapons
  - Armor
  - Tools
- **Enchantments**

## Milestone 1

El primer milestone se centra únicamente en:

- Core
- integración base con Cobblemon
- resolución Species -> Generation
- estado global de generaciones
- persistencia
- comandos
- control de spawning
- pruebas

Quests, Gacha, Equipment y Enchantments forman parte del alcance futuro y **no deben condicionar el núcleo con dependencias directas**.

## Entorno objetivo

- Minecraft 1.21.1
- NeoForge 1.21.1
- Java 21
- Cobblemon 1.8.x, inicialmente 1.8.1
- Youer 1.21.1 como entorno final de servidor
- AVECOINS 2.3 como integración económica prevista

## Principios

- Arquitectura modular.
- Core independiente de Cobblemon y AVECOINS cuando sea posible.
- Integraciones externas mediante puertos/adaptadores.
- Persistencia segura frente a reinicios y cierres.
- No asumir que una excepción económica implica que una mutación no ocurrió.
- Las rutas de spawning especiales se validarán con evidencia y pruebas runtime antes de considerarlas cubiertas.
- El proyecto nuevo se mantiene separado del código de implementaciones anteriores de Generation Control.

## AVECOINS

La integración económica futura tomará como referencia el patrón ya utilizado en Zian GTS:

```text
Core / Feature
      ↓
 EconomyPort
      ↓
AvecoinsEconomyPort
      ↓
AvecoinsWallet
      ↓
  AVECOINS 2.3
```

Los archivos de referencia se conservan en:

`docs/reference/avecoins/`

Estos archivos **no forman parte del código productivo de Zian Utilities**. Se guardan como documentación técnica para diseñar posteriormente la integración económica del nuevo proyecto.

## Estado de desarrollo

El bootstrap inicial del Milestone 1 usa dos módulos Gradle:

```text
core      -> dominio JVM puro, sin Minecraft / NeoForge / Cobblemon
neoforge  -> plataforma e integración con Minecraft / NeoForge / Cobblemon
```

Identidad productiva:

```text
Mod ID: zianutilities
Base package: com.zianblk.zianutilities
Java: 21
Gradle CI: 9.2.1
NeoForge baseline: 21.1.251
Cobblemon baseline: 1.8.1
```

La implementación funcional de Generation Control se construirá en slices posteriores del Milestone 1 sobre esta base.
