#!/bin/sh
# Executado pela imagem oficial do Postgres só na PRIMEIRA subida (volume vazio).
# Repassa as senhas do ambiente ao setup de privilégio mínimo; nada é gravado em arquivo.
set -eu
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
     -v senha_migracao="$CHEFHOME_SENHA_MIGRACAO" \
     -v senha_app="$CHEFHOME_SENHA_APP" \
     -v nome_banco="$POSTGRES_DB" \
     -f /db/setup/01_usuarios.sql
