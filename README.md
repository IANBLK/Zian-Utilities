# Zian Utilities

**Zian Utilities** es un mod modular para **Minecraft 1.21.1**, orientado principalmente a servidores con **Cobblemon 1.8+** sobre **NeoForge 1.21.1**, con compatibilidad final prevista para **Youer 1.21.1**.

> Estado actual: **fase de diseño e investigación**. El repositorio nace desde cero y no reutiliza la implementación del antiguo sistema de control de generaciones.

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

Todavía no se ha fijado la estructura Gradle ni el layout definitivo de paquetes. Se hará después de cerrar la arquitectura del Milestone 1 para evitar convertir decisiones provisionales en deuda técnica desde el primer commit.
