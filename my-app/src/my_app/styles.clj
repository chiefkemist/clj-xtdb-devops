(ns my-app.styles)

;; Layout
(def container
  "max-w-3xl mx-auto px-4 py-8")

(def page-container
  "min-h-screen bg-gray-50")

(def card
  "bg-white rounded-lg shadow-sm p-6")

;; Typography
(def heading-1
  "text-4xl font-bold text-gray-900 mb-6")

(def heading-2
  "text-2xl font-semibold text-gray-900 mb-4")

;; Forms
(def form-container
  "space-y-6")

(def form-group
  "space-y-1")

(def label
  "block text-sm font-medium text-gray-700")

(def input
  "mt-1 block w-full rounded-md border-gray-300 shadow-sm 
   focus:border-blue-500 focus:ring-blue-500 sm:text-sm")

(def textarea
  "mt-1 block w-full rounded-md border-gray-300 shadow-sm 
   focus:border-blue-500 focus:ring-blue-500 sm:text-sm h-32")

(def select
  "mt-1 block w-full rounded-md border-gray-300 shadow-sm 
   focus:border-blue-500 focus:ring-blue-500 sm:text-sm")

(def tag
  "inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium 
   bg-blue-100 text-blue-800 mr-2")

(def status-badge
  "inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium")

(def status-active
  "bg-green-100 text-green-800")

(def status-completed
  "bg-gray-100 text-gray-800")

(def status-pending
  "bg-yellow-100 text-yellow-800")

(def status-archived
  "bg-red-100 text-red-800")

(def priority-high
  "bg-red-100 text-red-800")

(def priority-medium
  "bg-yellow-100 text-yellow-800")

(def priority-low
  "bg-green-100 text-green-800")

;; Buttons
(def button-primary
  "inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium 
   rounded-md shadow-sm text-white bg-blue-600 hover:bg-blue-700 
   focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500")

(def button-danger
  "inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium 
   rounded-md shadow-sm text-white bg-red-600 hover:bg-red-700 
   focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500")

;; Lists
(def item-list
  "space-y-4")

(def item-list-item
  "bg-white rounded-lg shadow hover:shadow-md transition-all")

(def item-link
  "block p-4 hover:bg-gray-50")

(def item-content
  "flex items-center")

(def checkmark
  "text-blue-500 mr-3")

(def item-text
  "text-gray-900 font-medium")

(def divider
  "my-8 border-t border-gray-200")

;; Home page specific
(def welcome-section
  "min-h-screen flex flex-col justify-center items-center bg-white")

(def welcome-content
  "max-w-3xl w-full mx-auto text-center px-4")

(def welcome-heading
  "text-5xl font-bold text-gray-900 mb-4 tracking-tight")

(def welcome-description
  "text-xl text-gray-600 mb-8 max-w-2xl mx-auto")

(def features-container
  "bg-white rounded-xl shadow-lg p-8 mb-8")

(def feature-item
  "flex items-center text-gray-700 bg-gray-50 p-4 rounded-lg hover:bg-gray-100 transition")

(def get-started-button
  "inline-flex items-center px-6 py-3 border border-transparent text-base font-medium 
   rounded-full shadow-sm text-white bg-blue-600 hover:bg-blue-700 
   focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500")

;; Item details
(def item-details
  "space-y-4 bg-gray-50 rounded-lg p-4")

(def item-details-row
  "flex items-center gap-2")

(def item-details-label
  "font-semibold text-gray-700")

(def item-details-value
  "text-gray-900")

;; Navigation
(def nav-container
  "bg-white shadow-lg border-b border-gray-200 sticky top-0 z-50")

(def nav-content
  "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8")

(def nav-brand
  "text-2xl font-bold text-blue-600 hover:text-blue-700 transition-colors")

(def nav-list
  "flex items-center space-x-8")

(def nav-item
  "px-3 py-2 text-base font-medium text-gray-500 hover:text-gray-900 
   hover:bg-gray-100 rounded-md transition-all")

(def nav-item-active
  "px-3 py-2 text-base font-medium text-blue-600 bg-blue-50 
   rounded-md transition-all")

;; Tabs
(def tabs-list
  "flex space-x-8 border-b border-gray-200")

(def tab-item
  "py-4 px-4 text-sm font-medium text-gray-500 hover:text-gray-700 
   hover:border-gray-300 -mb-px")

(def tab-item-active
  "py-4 px-4 text-sm font-medium text-blue-600 border-b-2 border-blue-600 -mb-px")

;; Two column layout
(def two-column-container
  "grid grid-cols-4 gap-6")

(def sidebar
  "col-span-1")

(def main-content
  "col-span-3")

;; Search and filter styles
(def search-container
  "bg-white rounded-lg shadow-sm p-4 mb-6")

(def search-row
  "flex items-center gap-4 mb-4")

(def search-input
  "flex-1 rounded-md border-gray-300 shadow-sm 
   focus:border-blue-500 focus:ring-blue-500")

(def input-help
  "text-sm text-gray-500 ml-2")

(def button-secondary
  "inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium 
   rounded-md text-gray-700 bg-white hover:bg-gray-50 
   focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500")

;; Enhanced item display
(def item-header
  "flex justify-between items-center mb-2")

(def item-meta
  "flex items-center space-x-2")

(def meta-text
  "text-sm text-gray-500")

(def item-description
  "text-sm text-gray-600 line-clamp-2")

(def item-footer
  "flex justify-between items-center mt-2")

(def tags-container
  "flex flex-wrap gap-1")

(def header-row
  "flex justify-between items-center mb-6")

(def form-row
  "grid grid-cols-2 gap-4")

(def form-actions
  "flex justify-end space-x-4 mt-8") 