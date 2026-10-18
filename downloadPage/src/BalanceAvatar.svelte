<script lang="ts">
  import { balanceAvatarSources } from './balanceAvatars';
  let { id, name, officialUrl }: { id: number; name: string; officialUrl?: string } = $props();
  let attempt = $state(0);
  const sources = $derived(balanceAvatarSources(id, officialUrl));
  $effect(() => { void id; void officialUrl; attempt = 0; });
</script>

<span class="bd-avatar bd-avatar-fallback" aria-hidden="true">{name.charAt(0)}
  {#if sources[attempt]}
    <img class="bd-avatar-image" src={sources[attempt]} alt="" width="96" height="96" loading="lazy" decoding="async" onerror={() => attempt += 1}>
  {/if}
</span>
