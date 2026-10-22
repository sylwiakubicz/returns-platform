package dev.rma.returns.adapter.in.web;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.rma.returns.application.exception.InvalidReturnRequestException;
import dev.rma.returns.application.model.ReturnQuery;
import dev.rma.returns.application.port.in.GetReturnUseCase;
import dev.rma.returns.application.port.in.ListReturnsUseCase;
import dev.rma.returns.domain.enums.RmaStatus;

@RestController
@RequestMapping(value = "/api/v1/returns", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReturnQueryController {
    private final GetReturnUseCase getReturn;
    private final ListReturnsUseCase listReturns;

    public ReturnQueryController(GetReturnUseCase getReturn, ListReturnsUseCase listReturns) {
        this.getReturn = getReturn;
        this.listReturns = listReturns;
    }

    @GetMapping("/{businessNumber}")
    public ReturnResponse get(@PathVariable("businessNumber") String businessNumber) {
        if (!businessNumber.matches("RMA-[0-9]{4}-[0-9]{6}")) {
            throw new InvalidReturnRequestException("businessNumber", "Use the RMA-YYYY-NNNNNN business number");
        }
        return ReturnResponse.from(getReturn.get(businessNumber));
    }

    @GetMapping
    public PageResponse list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + ReturnQuery.DEFAULT_SIZE) int size,
            @RequestParam(name = "status", required = false) RmaStatus status) {
        ReturnQuery query;
        try {
            query = new ReturnQuery(page, size, status);
        } catch (IllegalArgumentException exception) {
            throw new InvalidReturnRequestException(page < 0 ? "page" : "size", exception.getMessage());
        }
        var result = listReturns.list(query);
        return new PageResponse(
                result.content().stream().map(ReturnResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    public record PageResponse(List<ReturnResponse> content, int page, int size, long totalElements, long totalPages) {}
}
