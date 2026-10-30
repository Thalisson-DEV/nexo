package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.BaseBusinessException;
import org.springframework.http.HttpStatus;

public class ConflitoGovernancaSstException extends BaseBusinessException {

    public ConflitoGovernancaSstException(String detail) {
        super("Conflito de governança de SST", detail, HttpStatus.CONFLICT,
                "sst-governanca-conflito");
    }
}
