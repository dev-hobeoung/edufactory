import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Label, LabelRef, MaterialSummary } from '../api/types';

/** 여러 화면이 공유하는 목록 (라벨·자료 선택기) */
export function useLabels() {
  return useQuery({ queryKey: ['labels'], queryFn: api.labels, staleTime: 30_000 });
}

export function useMaterialOptions() {
  return useQuery({ queryKey: ['materials', 'options'], queryFn: () => api.materials(), staleTime: 30_000 });
}

export function useSettings() {
  return useQuery({ queryKey: ['settings'], queryFn: api.settings, staleTime: 60_000 });
}

/** 라벨을 그룹별로 묶는다 (그룹 이름순, 그룹 안은 이름순) */
export function groupLabels<T extends LabelRef>(labels: T[]): [string, T[]][] {
  const map = new Map<string, T[]>();
  for (const l of labels) map.set(l.group, [...(map.get(l.group) ?? []), l]);
  return [...map.entries()]
    .sort(([a], [b]) => a.localeCompare(b, 'ko'))
    .map(([g, list]) => [g, [...list].sort((a, b) => a.name.localeCompare(b.name, 'ko'))]);
}

export function labelById(labels: Label[] | undefined, id: string): Label | undefined {
  return labels?.find((l) => l.id === id);
}

export function materialById(list: MaterialSummary[] | undefined, id: string): MaterialSummary | undefined {
  return list?.find((m) => m.id === id);
}
