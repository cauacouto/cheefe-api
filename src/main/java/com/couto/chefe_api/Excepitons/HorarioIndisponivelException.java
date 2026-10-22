package com.couto.chefe_api.Excepitons;

/** O chef já tem um agendamento confirmado que se sobrepõe a esse horário (409). */
public class HorarioIndisponivelException extends ConflitoException {

    public HorarioIndisponivelException(String nomeChef) {
        super("O chef " + nomeChef + " já tem um agendamento confirmado nesse horário");
    }
}
