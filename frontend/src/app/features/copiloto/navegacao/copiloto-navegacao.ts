import { AssistenteAcao } from '../../../core/models/assistente.models';
import {
  COPILOTO_DESTINOS,
  NavigationParameter
} from './copiloto-destinos';

export interface ResolvedChatNavigation {
  key: string;
  route: string;
  queryParams: Record<string, string>;
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const UNRESOLVED_PATH_PARAMETER = /:[A-Za-z][A-Za-z0-9_]*/;

export function resolveChatNavigation(
  action: AssistenteAcao,
  userAuthorities: readonly string[]
): ResolvedChatNavigation | null {
  if (action?.tipo !== 'NAVIGATE' || !isNonEmptyString(action.rotulo)) return null;

  const key = action.chaveDestino ?? action.destino;
  if (!isNonEmptyString(key)) return null;

  const target = COPILOTO_DESTINOS[key];
  if (!target || action.autoridade !== target.routeAuthority) return null;
  if (!userAuthorities.includes(target.routeAuthority)) return null;

  const providedParameters = normalizeParameters(action.parametros);
  if (!providedParameters) return null;

  const declaredNames = new Set(target.parameters.map(parameter => parameter.name));
  if (Object.keys(providedParameters).some(name => !declaredNames.has(name))) return null;

  let route = target.route;
  const queryParams: Record<string, string> = {};
  for (const parameter of target.parameters) {
    const hasValue = Object.prototype.hasOwnProperty.call(providedParameters, parameter.name);
    if (!hasValue) {
      if (parameter.required) return null;
      continue;
    }

    const value = providedParameters[parameter.name];
    if (!isValidParameterValue(value, parameter)) return null;

    if (parameter.destination === 'PATH') {
      const token = `:${parameter.name}`;
      if (!route.includes(token)) return null;
      route = route.replace(token, encodeURIComponent(value));
    } else {
      queryParams[parameter.name] = value;
    }
  }

  if (UNRESOLVED_PATH_PARAMETER.test(route)) return null;
  return { key, route, queryParams };
}

function normalizeParameters(
  parameters: Record<string, unknown> | null | undefined
): Record<string, unknown> | null {
  if (parameters === null || parameters === undefined) return {};
  if (typeof parameters !== 'object' || Array.isArray(parameters)) return null;
  return parameters;
}

function isValidParameterValue(
  value: unknown,
  parameter: NavigationParameter
): value is string {
  if (typeof value !== 'string' || !value.length) return false;
  if (parameter.type === 'UUID') return UUID_PATTERN.test(value);
  return parameter.type === 'ENUM' && (parameter.allowedValues?.includes(value) ?? false);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}
