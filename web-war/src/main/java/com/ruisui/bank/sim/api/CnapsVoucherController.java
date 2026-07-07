package com.ruisui.bank.sim.api;

import com.ruisui.bank.sim.api.dto.HeaderContext;
import com.ruisui.bank.sim.api.dto.DeleteRequest;
import com.ruisui.bank.sim.api.dto.ReviewPassRequest;
import com.ruisui.bank.sim.api.dto.ReviewReturnRequest;
import com.ruisui.bank.sim.api.dto.VoucherCreateRequest;
import com.ruisui.bank.sim.api.dto.VoucherResponse;
import com.ruisui.bank.sim.service.CnapsVoucherService;
import com.ruisui.bank.sim.service.HeaderContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/cnaps/vouchers")
public class CnapsVoucherController {
    private final CnapsVoucherService voucherService;
    private final HeaderContextResolver headerContextResolver;

    public CnapsVoucherController(CnapsVoucherService voucherService, HeaderContextResolver headerContextResolver) {
        this.voucherService = voucherService;
        this.headerContextResolver = headerContextResolver;
    }

    @PostMapping
    public ApiResponse<VoucherResponse> create(HttpServletRequest request, @RequestBody VoucherCreateRequest createRequest) {
        HeaderContext context = headerContextResolver.resolve(request);
        return ApiResponse.ok(context.requestId(), "success", voucherService.create(context, createRequest));
    }

    @GetMapping
    public ApiResponse<Page<VoucherResponse>> query(
        HttpServletRequest request,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String operatorNo,
        @RequestParam(required = false) String serialNo,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        HeaderContext context = headerContextResolver.resolve(request);
        return ApiResponse.ok(context.requestId(), "success", voucherService.query(context, status, operatorNo, serialNo, page, size));
    }

    @GetMapping("/review-list")
    public ApiResponse<Page<VoucherResponse>> reviewList(
        HttpServletRequest request,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        HeaderContext context = headerContextResolver.resolve(request);
        return ApiResponse.ok(context.requestId(), "success", voucherService.reviewList(context, page, size));
    }

    @GetMapping("/{billId}")
    public ApiResponse<VoucherResponse> detail(HttpServletRequest request, @PathVariable String billId) {
        HeaderContext context = headerContextResolver.resolve(request);
        return ApiResponse.ok(context.requestId(), "success", voucherService.detail(context, billId));
    }

    @PutMapping("/{billId}")
    public ApiResponse<VoucherResponse> update(
        HttpServletRequest request,
        @PathVariable String billId,
        @RequestBody VoucherCreateRequest updateRequest
    ) {
        HeaderContext context = headerContextResolver.resolve(request);
        return ApiResponse.ok(context.requestId(), "操作已成功", voucherService.update(context, billId, updateRequest));
    }

    @PostMapping("/{billId}/delete")
    public ApiResponse<VoucherResponse> delete(
        HttpServletRequest request,
        @PathVariable String billId,
        @RequestBody DeleteRequest deleteRequest
    ) {
        HeaderContext context = headerContextResolver.resolve(request);
        return ApiResponse.ok(context.requestId(), "操作已成功", voucherService.delete(context, billId, deleteRequest));
    }

    @PostMapping("/{billId}/review-pass")
    public ApiResponse<VoucherResponse> reviewPass(
        HttpServletRequest request,
        @PathVariable String billId,
        @RequestBody ReviewPassRequest reviewPassRequest
    ) {
        HeaderContext context = headerContextResolver.resolve(request);
        return ApiResponse.ok(context.requestId(), "操作已成功", voucherService.reviewPass(context, billId, reviewPassRequest));
    }

    @PostMapping("/{billId}/review-return")
    public ApiResponse<VoucherResponse> reviewReturn(
        HttpServletRequest request,
        @PathVariable String billId,
        @RequestBody ReviewReturnRequest reviewReturnRequest
    ) {
        HeaderContext context = headerContextResolver.resolve(request);
        return ApiResponse.ok(context.requestId(), "操作已成功", voucherService.reviewReturn(context, billId, reviewReturnRequest));
    }
}
