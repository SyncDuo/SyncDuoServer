package com.syncduo.server.workflow.controller;

import com.syncduo.server.workflow.model.api.global.FlowResponse;
import com.syncduo.server.workflow.model.api.info.FlowInfoDTO;
import com.syncduo.server.workflow.service.FlowMsgPersistService;
import com.syncduo.server.workflow.service.FlowScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@CrossOrigin(originPatterns = "*")
@RequestMapping("/flow-info")
@RequiredArgsConstructor
public class FlowInfoController {
    private final FlowMsgPersistService flowMsgPersistService;
    private final FlowScheduler flowScheduler;

    @GetMapping("/get-all-flow-info")
    public FlowResponse<List<FlowInfoDTO>> getAllFlowInfo() {
        return FlowResponse.success(flowMsgPersistService.getAllFlowInfo());
    }

    @PostMapping("/enable-flow")
    public FlowResponse<Void> enableFlow(@RequestBody FlowInfoDTO flowInfoDTO) {
        this.flowScheduler.enableSchedule(flowInfoDTO.getFlowDefinitionId());
        return FlowResponse.success();
    }

    @PostMapping("/disable-flow")
    public FlowResponse<Void> disableFlow(@RequestBody FlowInfoDTO flowInfoDTO) {
        this.flowScheduler.stopSchedule(flowInfoDTO.getFlowDefinitionId());
        return FlowResponse.success();
    }

    @PostMapping("/delete-flow")
    public FlowResponse<Void> deleteFlow(@RequestBody FlowInfoDTO flowInfoDTO) {
        this.flowScheduler.deleteFlow(flowInfoDTO.getFlowDefinitionId());
        return FlowResponse.success();
    }
}
