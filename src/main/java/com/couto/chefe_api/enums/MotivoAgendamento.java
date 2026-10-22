package com.couto.chefe_api.enums;

/** Motivo pré-definido de um cancelamento, recusa ou desistência; o código vai para o banco e o rótulo para as telas. */
public interface MotivoAgendamento {

    String name();

    String rotulo();
}
