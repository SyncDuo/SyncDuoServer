package com.syncduo.server.model.restic.cat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CatConfig {

    private int version;

    private String id;

    @JsonProperty("chunker_polynomial")
    private String chunkerPolynomial;
}
