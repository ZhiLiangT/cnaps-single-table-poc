package com.ruisui.bank.sim.api;

import com.ruisui.bank.sim.domain.BusinessException;
import com.ruisui.bank.sim.domain.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReferenceController {

    private static final List<String> SERVICES = List.of(
        "SYSHEALTH", "DICTQRY", "BANKQRY", "CNAPS5701E", "CNAPS5701U",
        "CNAPS5701D", "CNAPS4609Q", "CNAPS5702Q", "CNAPS5702I",
        "CNAPS5702A", "CNAPS5702R"
    );

    private static final Map<String, List<DictItem>> DICTS = Map.of(
        "BUSINESS_TYPE", List.of(new DictItem("02102", "普通汇兑")),
        "PRIORITY", List.of(new DictItem("NORM", "普通")),
        "FEE_CHARGE_MODE", List.of(new DictItem("1", "同城收费")),
        "SEND_MODE", List.of(new DictItem("0", "柜面")),
        "DEBIT_MODE", List.of(new DictItem("1", "扣收")),
        "FAX_FLAG", List.of(new DictItem("0", "否"), new DictItem("1", "是")),
        "SYSTEM_TYPE", List.of(new DictItem("CNAPS", "CNAPS"))
    );

    private static final List<BankItem> BANKS = List.of(
        new BankItem("102290000002", "接收行名称")
    );

    @GetMapping("/health")
    public ApiResponse<HealthData> health() {
        return ApiResponse.ok(null, "success", new HealthData("UP", SERVICES));
    }

    @GetMapping("/dicts/{dictType}")
    public ApiResponse<List<DictItem>> dicts(@PathVariable String dictType) {
        List<DictItem> items = DICTS.get(dictType);
        if (items == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Unknown dictionary type: " + dictType);
        }
        return ApiResponse.ok(null, "success", items);
    }

    @GetMapping("/banks")
    public ApiResponse<List<BankItem>> banks(@RequestParam(required = false) String bankNo) {
        if (bankNo == null || bankNo.isBlank()) {
            return ApiResponse.ok(null, "success", BANKS);
        }
        return ApiResponse.ok(null, "success",
            BANKS.stream().filter(bank -> bank.bankNo().equals(bankNo)).toList());
    }

    public record HealthData(String status, List<String> services) {
    }

    public record DictItem(String code, String name) {
    }

    public record BankItem(String bankNo, String bankName) {
    }
}
