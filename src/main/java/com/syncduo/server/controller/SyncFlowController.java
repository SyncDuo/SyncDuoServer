package com.syncduo.server.controller;

import com.syncduo.server.model.dto.http.SyncFlowRequest;
import com.syncduo.server.model.dto.http.SyncFlowResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("/sync-flow")
public class SyncFlowController {

    @PostMapping("/source-2-internal")
    public SyncFlowResponse addSource2InternalSyncFlow(@RequestBody SyncFlowRequest syncFlowRequest) {

        return null;
    }

    @PostMapping("/internal-2-content")
    public SyncFlowResponse addInternal2ContentSyncFlow(@RequestBody SyncFlowRequest syncFlowRequest) {

        return null;
    }
}
