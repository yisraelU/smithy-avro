# Smithy-Avro

### CREDITS 
This work is inspired and very often copied from the work done by the Disney Streaming team on the [smithy-translate](https://github.com/disneystreaming/smithy-translate) project.
Attribution is included on a per file basis.


### GOAL
The goal of this project is to provide a set of tools to translate [Smithy](https://smithy.io) models into [Avro](https://avro.apache.org/) models.

### STATUS
This project is in the early stages of development.  It is not ready for use.

### BUILD
This project uses [sbt](https://www.scala-sbt.org/) as its build tool.  To build the project run the following command from the root of the project.

```bash
sbt compile
```

### MODULES
This project is broken into several modules.  The modules are as follows:
    - core
    - avro
    - cli

#### core
The core module contains the core logic for translating Smithy models into Avro models.  It also contains the logic for translating Smithy models into Avro schema files.
See below for the mapping of Smithy shapes to Avro schema types.

#### avro
The avro module is a Java project that contains the `Smithy Traits` to enable and customize Avro generation.

#### cli
The cli module is a Scala project that contains a simple command line interface for translating Smithy models into Avro models.
It is built using [decline](https://ben.kirw.in/decline/)


- java traits
- java validators
- service provider files
- smithy files


# later
- simple cli
- scala avro model
- scala avro file model
- shape visitor for avro model
- shape visitor for avro file model

# Primitive Mapping
  - blob -> Avro bytes
  - boolean -> Avro boolean
  - string -> Avro string
  - byte -> Avro int
  - short -> Avro int
  - integer -> Avro int
  - long -> Avro long
  - float -> Avro float
  - double -> Avro double
  - bigInteger -> Avro Long
  - bigDecimal -> Avro Decimal Logical type with a scale and precision defined by trait
  - timestamp -> Avro Timestamp Logical type requires a trait to define the format
  - document -> Avro String
  - enum -> Avro Enum

# Collection Mapping
  - list -> Avro Array
  - map -> Avro Map

# Complex Type Mapping
  - structure -> Avro Record
  - union -> Avro Union

# Service Mapping (not implemented)
  - service -> Avro Rpc

# Smithy traits to Avro Annotations Mapping 
  - @default -> Avro default
  - @documentation -> Avro doc

# Additional Smithy traits provided
  - 



