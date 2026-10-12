# Gachas y SQLite

La comprobación de existencia ya consultaba SQLite, pero NbtIo.readCompressed(Path) y writeCompressed(Path) accedían directamente a archivos antiguos. Se usan los mismos métodos NBT sobre los streams de StorageFiles. Se conservan el límite de descompresión, escritura temporal, confirmación durable y reemplazo atómico.

La prueba importa pools.nbt, retira la carpeta antigua, lee los premios existentes, escribe un estado DEBIT_PENDING y vuelve a abrirlo desde SQLite. Los premios y estados inciertos se preservan; no se recrean los pools para ocultar el fallo.
