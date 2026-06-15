import { mount } from 'svelte';
import App from './App.svelte';
import '../base.css';
import '../download.css';

const target = document.getElementById('app');

if (!target) {
  throw new Error('Missing #app mount target');
}

mount(App, { target });
