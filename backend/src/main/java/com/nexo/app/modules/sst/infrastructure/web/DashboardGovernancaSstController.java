package com.nexo.app.modules.sst.infrastructure.web;
import com.nexo.app.modules.sst.application.dto.DashboardGovernancaSstDto;import com.nexo.app.modules.sst.application.usecases.DashboardGovernancaSstUseCases;import lombok.RequiredArgsConstructor;import org.springdoc.core.annotations.ParameterObject;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/v1/sst/governanca/dashboard") @RequiredArgsConstructor
public class DashboardGovernancaSstController{private final DashboardGovernancaSstUseCases useCases;
 @GetMapping @PreAuthorize("hasAuthority('sst:governanca:read')") public DashboardGovernancaSstDto.Response consultar(@ParameterObject DashboardGovernancaSstDto.Filter filtro){return useCases.consultar(filtro);}}
