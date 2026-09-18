import { mount } from 'svelte';
import NavApp from './NavApp.svelte';
import '../../base.css';
import './nav.css';

const target = document.getElementById('nav-app');

if (!target) {
  throw new Error('Missing #nav-app mount target');
}

mount(NavApp, { target });
