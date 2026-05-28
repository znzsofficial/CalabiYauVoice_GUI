import { mount } from 'svelte';
import SearchApp from './SearchApp.svelte';
import '../../search/search.css';

const target = document.getElementById('search-app');

if (!target) {
  throw new Error('Missing #search-app mount target');
}

mount(SearchApp, { target });
